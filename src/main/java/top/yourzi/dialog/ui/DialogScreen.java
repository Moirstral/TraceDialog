package top.yourzi.dialog.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import org.lwjgl.glfw.GLFW;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.config.ClientConfig;
import top.yourzi.dialog.config.ServerConfig;
import top.yourzi.dialog.model.BackgroundAnimationType;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogOption;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话界面，用于显示对话框和立绘
 */
@SuppressWarnings("removal")
public class DialogScreen extends Screen {
    // 对话序列和当前对话条目
    private final DialogSequence dialogSequence;
    private final DialogEntry dialogEntry;
    // 选项按钮列表
    private final List<OptionButton> optionButtons = new ArrayList<>();
    // 玩家名称
    private final String playerName;
    //立绘数据列表
    private final List<PortraitDisplayData> portraitDisplayList = new ArrayList<>();
    // 需要在对话中显示的物品列表
    private final List<ItemStack> displayItemStacks = new ArrayList<>();
    // 背景图片相关
    private BackgroundImageDisplayData backgroundImageDisplayData;
    private long backgroundFadeStartTime = 0; // 背景图片淡入开始时间
    private long backgroundFadeOutStartTime = 0; // 背景图片淡出开始时间
    private boolean isClosing = false; // 是否正在关闭
    // 对话框位置和大小
    private int dialogBoxX;
    private int dialogBoxY;
    private int dialogBoxWidth;
    private int dialogBoxHeight;
    // 对话框背景图片
    private final String dialogBackgroundImagePath;
    // 文本动画相关
    private int currentCharIndex = 0;
    private long lastCharTime = 0;
    private boolean textFullyDisplayed = false;
    // 快速跳过相关
    private int fastForwardCooldown = 0;
    private boolean optionButtonsCreated = false; // 标记选项按钮是否已为当前条目创建
    // 对话历史记录界面相关
    private boolean showingHistory = false;
    private int historyScrollOffset = 0;
    private List<DialogEntry> historyEntries = new ArrayList<>();
    private ImageButton closeHistoryButton; // 关闭历史记录按钮
    private ImageButton viewHistoryButton; // 查看历史按钮
    private ImageButton autoPlayButton; // 自动播放按钮
    // 滚动条相关
    private int totalHistoryContentHeight = 0;
    private boolean canScrollHistoryDown = false;
    private boolean canScrollHistoryUp = false;
    private static final WidgetSprites DEFAULT_BUTTON_SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/button"), ResourceLocation.withDefaultNamespace("widget/button_highlighted")
    );

    public HolderLookup.Provider levelRegistryAccess() {
        return Minecraft.getInstance().level.registryAccess();
    }

    // 历史记录音频播放相关
    private final List<HistoryAudioButton> historyAudioButtons = new ArrayList<>();
    private SimpleSoundInstance currentHistoryAudio = null; // 当前播放的历史记录音频

    // 说话实体（可选）
    private final net.minecraft.world.entity.Entity speakerEntity;

    // 音频播放现在由DialogManager全局管理
    public DialogScreen(DialogSequence dialogSequence, DialogEntry dialogEntry, String playerName) {
        this(dialogSequence, dialogEntry, playerName, null);
    }

    /**
     * 带说话实体的构造函数
     */
    public DialogScreen(DialogSequence dialogSequence, DialogEntry dialogEntry, String playerName, net.minecraft.world.entity.Entity speakerEntity) {
        super(dialogEntry.getSpeaker(Minecraft.getInstance().level.registryAccess(), playerName) != null ? dialogEntry.getSpeaker(Minecraft.getInstance().level.registryAccess(), playerName) : Component.empty());
        this.dialogSequence = dialogSequence;
        this.dialogEntry = dialogEntry;
        this.playerName = playerName;
        this.speakerEntity = speakerEntity;
        this.font = Minecraft.getInstance().font;

        // 加载背景图片资源
        if (dialogEntry.getBackgroundImage() != null && dialogEntry.getBackgroundImage().getPath() != null && !dialogEntry.getBackgroundImage().getPath().isEmpty()) {
            this.backgroundImageDisplayData = new BackgroundImageDisplayData(dialogEntry.getBackgroundImage());
            // 根据动画类型设置动画开始时间
            if (this.backgroundImageDisplayData.animationType == BackgroundAnimationType.FADE_IN) {
                this.backgroundImageDisplayData.animationStartTime = System.currentTimeMillis();
            }
            this.backgroundFadeStartTime = System.currentTimeMillis(); // 初始化背景淡入开始时间
        }

        if (dialogEntry.getDialogImage() != null && !dialogEntry.getDialogImage().isEmpty()) {
            this.dialogBackgroundImagePath = dialogEntry.getDialogImage();
        } else {
            this.dialogBackgroundImagePath = "textures/dialog_background/background.png";
        }

        // 加载多个立绘资源
        if (dialogEntry.getPortraits() != null && !dialogEntry.getPortraits().isEmpty()) {
            for (top.yourzi.dialog.model.PortraitInfo portraitInfo : dialogEntry.getPortraits()) {
                if (portraitInfo.getPath() != null && !portraitInfo.getPath().isEmpty()) {
                    PortraitDisplayData displayData = new PortraitDisplayData(
                            portraitInfo.getPath(),
                            portraitInfo.getBrightness(),
                            portraitInfo.getPosition(),
                            portraitInfo.getAnimationType(),
                            portraitInfo.getSize()
                    );
                    if (displayData.loadedSuccessfully) {
                        this.portraitDisplayList.add(displayData);
                    }
                } else {
                    Dialog.LOGGER.warn("Encountered a portrait info with null or empty path.");
                }
            }
        } else {
            Dialog.LOGGER.warn("No portrait configurations found in DialogEntry or the list is empty.");
        }

        // 加载需要在对话中显示的物品
        if (dialogEntry.getDisplayItems() != null && !dialogEntry.getDisplayItems().isEmpty()) {
            for (top.yourzi.dialog.model.DisplayItemInfo itemInfo : dialogEntry.getDisplayItems()) {
                if (itemInfo.getItemId() != null && !itemInfo.getItemId().isEmpty()) {
                    try {
                        ResourceLocation itemRl = ResourceLocation.parse(itemInfo.getItemId());
                        Item item = BuiltInRegistries.ITEM.get(itemRl);
                        if (item != null && item != Items.AIR) {
                            ItemStack itemStack = new ItemStack(item, itemInfo.getCount() > 0 ? itemInfo.getCount() : 1);
                            if (itemInfo.getNbt() != null && !itemInfo.getNbt().isEmpty()) {
                                try {
                                    CompoundTag nbtTag = TagParser.parseTag(itemInfo.getNbt());
                                    itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbtTag));
                                } catch (Exception e) {
                                    Dialog.LOGGER.error("Error parsing NBT for display item {}: {}. NBT: '{}'", itemInfo.getItemId(), e.getMessage(), itemInfo.getNbt());
                                }
                            }
                            this.displayItemStacks.add(itemStack);
                        } else {
                            Dialog.LOGGER.warn("Item not found or is AIR: {}. Skipping display item.", itemInfo.getItemId());
                        }
                    } catch (Exception e) {
                        Dialog.LOGGER.error("Error creating ItemStack for display item {}: {}", itemInfo.getItemId(), e.getMessage());
                    }
                } else {
                    Dialog.LOGGER.warn("Encountered a display item with null or empty itemId.");
                }
            }
        }

        // 检查是否由快速跳过触发
        if (DialogManager.isFastForwardingNext()) {
            this.fastForwardCooldown = 5;
            DialogManager.setFastForwardingNext(false); // 重置标记
        }

        // 初始化音频播放
        initializeAudio();
    }

    /**
     * 初始化音频播放
     */
    private void initializeAudio() {
        // 如果当前对话条目有音频路径且不是快进模式，则开始播放音频
        if (dialogEntry.getAudioPath() != null && !dialogEntry.getAudioPath().isEmpty() && fastForwardCooldown == 0) {
            DialogManager.playDialogAudio(dialogEntry.getAudioPath());
        }
    }

    // 音频播放方法已移至DialogManager进行全局管理

    @Override
    protected void init() {
        super.init();

        // 设置对话框位置和大小
        dialogBoxWidth = ClientConfig.DIALOG_BOX_WIDTH.get();
        dialogBoxHeight = ClientConfig.DIALOG_BOX_HEIGHT.get();
        dialogBoxX = (width - dialogBoxWidth) / 2;
        dialogBoxY = height - dialogBoxHeight - 20;

        // 初始化查看历史按钮 (位于对话框右下角)
        int historyButtonWidth = 20;
        int historyButtonHeight = 20;
        int historyButtonPadding = 5;

        ResourceLocation buttonTexture = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "widget/button");
        ResourceLocation buttonHighlightTexture = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "widget/button_highlighted");
        WidgetSprites buttonTextureS = ClientConfig.USE_CUSTOM_BUTTON_TEXTURE.get() ? new WidgetSprites(buttonTexture, buttonHighlightTexture) : DEFAULT_BUTTON_SPRITES;
        this.viewHistoryButton = new OptionButton(
                dialogBoxX + dialogBoxWidth - historyButtonWidth - historyButtonPadding,
                dialogBoxY + dialogBoxHeight - historyButtonHeight - historyButtonPadding,
                historyButtonWidth, historyButtonHeight, buttonTextureS,
                button -> toggleHistoryScreen(),
                Component.literal("▲")
        );
        addRenderableWidget(this.viewHistoryButton);

        // 初始化自动播放按钮 (位于历史记录按钮左侧)
        int autoPlayButtonWidth = 20;
        int autoPlayButtonHeight = 20;

        this.autoPlayButton = new OptionButton(
                dialogBoxX + dialogBoxWidth - historyButtonWidth - historyButtonPadding - autoPlayButtonWidth - historyButtonPadding,
                dialogBoxY + dialogBoxHeight - autoPlayButtonHeight - historyButtonPadding,
                autoPlayButtonWidth, autoPlayButtonHeight, buttonTextureS,
                button -> toggleAutoPlay(),
                Component.literal("▶")
        );
        addRenderableWidget(this.autoPlayButton);
        updateAutoPlayButtonText(); // 初始化按钮文本

        // 如果此对话条目有选项，预先停止自动播放
        if (dialogEntry.hasOptions()) {
            if (DialogManager.isAutoPlaying()) {
                DialogManager.stopAutoPlay();
                updateAutoPlayButtonText(); // 更新按钮文本以反映自动播放已停止
            }
        }
        this.optionButtonsCreated = false; // 初始化选项按钮创建标记

        // 初始化关闭历史记录按钮 (用于关闭历史查看界面)
        int closeButtonWidth = 60;
        int closeButtonHeight = 20;

        this.closeHistoryButton = new OptionButton(
                this.width / 2 - closeButtonWidth / 2, this.height - 30,
                closeButtonWidth, closeButtonHeight, buttonTextureS,
                button -> toggleHistoryScreen(),
                Component.literal("-▼-")
        );
    }

    /**
     * 创建对话选项按钮
     */
    private void createOptionButtons() {
        optionButtons.clear();

        DialogOption[] options = dialogEntry.getOptions();
        if (options == null || options.length == 0) {
            return;
        }

        int buttonWidth = 200; // 默认值
        int buttonHeight = 20; // 默认值

        // 优先使用本地自定义按钮图集，如果没有则使用原版纹理
        ResourceLocation buttonTexture = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "widget/button");
        ResourceLocation buttonHighlightTexture = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "widget/button_highlighted");

        int buttonSpacing = 5;
        int totalHeight = options.length * (buttonHeight + buttonSpacing) - buttonSpacing;

        // 计算选项按钮的起始Y位置，如果有物品显示则需要额外上移
        int startY = dialogBoxY - totalHeight - 10;
        if (!this.displayItemStacks.isEmpty()) {
            // 物品显示区域高度：itemSize(16) + 间距(5) + 额外间距(10)
            int itemDisplayHeight = 16 + 5 + 10;
            startY -= itemDisplayHeight;
        }


        for (int i = 0; i < options.length; i++) {
            DialogOption option = options[i];
            int buttonY = startY + i * (buttonHeight + buttonSpacing);

            OptionButton button = addRenderableWidget(new OptionButton(
                    (width - buttonWidth) / 2, // xPos
                    buttonY,                   // yPos
                    buttonWidth,               // width
                    buttonHeight,              // height
                    ClientConfig.USE_CUSTOM_BUTTON_TEXTURE.get() ? new WidgetSprites(buttonTexture, buttonHighlightTexture) : DEFAULT_BUTTON_SPRITES,
                    b -> {                     // onPress
                        // 执行选项指令（如果存在）
                        if (option.getCommand() != null && !option.getCommand().isEmpty()) {
                            DialogManager.getInstance().executeCommands(this.getMinecraft().player, option.getCommand(), this.speakerEntity);
                        }
                        DialogManager.getInstance().recordChoiceForCurrentDialog(option.getText(levelRegistryAccess(), playerName).getString());
                        DialogManager.getInstance().jumpToDialog(option.getTargetId());
                    },
                    option.getText(levelRegistryAccess(), playerName) // Component message
            ));

            optionButtons.add(button);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {

        // 首先渲染背景图片 (如果存在且加载成功)
        if (this.backgroundImageDisplayData != null && this.backgroundImageDisplayData.loadedSuccessfully) {
            DialogManager.renderBackgroundImage(guiGraphics, this.backgroundImageDisplayData, isClosing, backgroundFadeOutStartTime, this.width, this.height);
        }

        // 如果正在显示历史记录，则渲染历史记录界面
        if (showingHistory) {
            renderHistoryScreen(guiGraphics, mouseX, mouseY, partialTicks);
            // 渲染历史记录界面的关闭按钮
            this.closeHistoryButton.render(guiGraphics, mouseX, mouseY, partialTicks);
            return; // 不渲染对话框和立绘
        }

        // 渲染立绘
        if (!portraitDisplayList.isEmpty()) {
            DialogManager.renderPortrait(guiGraphics, portraitDisplayList, this.width, this.height, 0.7f, this.dialogBoxX, 0);
        }

        // 渲染对话框背景
        DialogManager.renderDialogBackground(guiGraphics, dialogBackgroundImagePath, this.dialogBoxX, this.dialogBoxY, this.dialogBoxWidth, this.dialogBoxHeight);

        // 如果自动播放开启且无选项，显示提示
        if (DialogManager.isAutoPlaying() && !dialogEntry.hasOptions()) {
            Component autoPlayText = Component.translatable("dialog.ui.auto");
            int autoPlayTextWidth = this.font.width(autoPlayText);
            // 将提示显示在对话框的右上角外部一点或者左上角，避免遮挡按钮
            guiGraphics.drawString(this.font, autoPlayText, dialogBoxX + dialogBoxWidth - autoPlayTextWidth - 5, dialogBoxY - 15, 0xFFFFFF);
        }

        // 渲染对话文本
        int padding = ClientConfig.DIALOG_BOX_PADDING.get();
        int textX = dialogBoxX + padding;
        int textY = dialogBoxY + padding;

        // 如果显示说话者名称且有说话者
        Component speakerComponent = dialogEntry.getSpeaker(levelRegistryAccess(), playerName);
        if (ClientConfig.SHOW_SPEAKER_NAME.get() && speakerComponent != null && !speakerComponent.getString().isEmpty()) {
            guiGraphics.drawString(font, speakerComponent, textX, textY, 0xFFFFFF);
            textY += font.lineHeight + 5;
        }

        // 渲染对话文本
        Component text = dialogEntry.getText(levelRegistryAccess(), playerName);
        String rawText = text.getString();
        if (rawText != null && !rawText.isEmpty()) {
            int maxWidth = dialogBoxWidth - (padding * 2);
            int textAnimationSpeed = ClientConfig.TEXT_ANIMATION_SPEED.get(); // 每秒字符数

            if (textAnimationSpeed <= 0) { // 立即显示
                textFullyDisplayed = true;
                currentCharIndex = rawText.length();
            }

            if (!textFullyDisplayed) {
                long currentTime = System.currentTimeMillis();
                if (lastCharTime == 0) { // 首次渲染或重置
                    lastCharTime = currentTime;
                }
                // 计算每字符间隔时间 (毫秒)
                long charInterval = (textAnimationSpeed > 0) ? (1000 / textAnimationSpeed) : 0;

                if (currentTime - lastCharTime >= charInterval) {
                    currentCharIndex++;
                    lastCharTime = currentTime;
                    if (currentCharIndex >= rawText.length()) {
                        textFullyDisplayed = true;
                        currentCharIndex = rawText.length(); // 确保索引不超过长度
                        lastCharTime = System.currentTimeMillis(); // 记录文本完全显示的时间点，用于自动播放计时
                    }
                }
            }

            // 渲染对话中展示的物品
            if (!this.displayItemStacks.isEmpty() && textFullyDisplayed) {
                int itemSize = 16;
                int itemPadding = 4;
                int totalItemWidth = (this.displayItemStacks.size() * itemSize) + (Math.max(0, this.displayItemStacks.size() - 1) * itemPadding);

                int startX = dialogBoxX + (dialogBoxWidth - totalItemWidth) / 2;
                int itemY = dialogBoxY - itemSize - 5;

                for (ItemStack itemStack : this.displayItemStacks) {

                    guiGraphics.renderItem(itemStack, startX, itemY);

                    if (mouseX >= startX && mouseX < startX + itemSize && mouseY >= itemY && mouseY < itemY + itemSize) {
                        guiGraphics.fill(startX, itemY, startX + itemSize, itemY + itemSize, 0x80000000);
                    }

                    guiGraphics.renderItemDecorations(this.font, itemStack, startX, itemY);

                    startX += itemSize + itemPadding;
                }

                startX = dialogBoxX + (dialogBoxWidth - totalItemWidth) / 2;
                for (ItemStack itemStack : this.displayItemStacks) {
                    if (mouseX >= startX && mouseX < startX + itemSize && mouseY >= itemY && mouseY < itemY + itemSize) {
                        guiGraphics.renderTooltip(this.font, itemStack, mouseX, mouseY);
                    }
                    startX += itemSize + itemPadding;
                }
            }

            // 如果自动播放开启，且文本完全显示，且没有选项，则延迟后自动前进
            if (DialogManager.isAutoPlaying() && textFullyDisplayed && !dialogEntry.hasOptions()) {
                boolean canAutoAdvance = false;

                // 如果当前对话有音频，等待音频播放完毕再跳转
                if (dialogEntry.getAudioPath() != null && !dialogEntry.getAudioPath().isEmpty()) {
                    if (DialogManager.isAudioFinished()) {
                        canAutoAdvance = true;
                    }
                } else {
                    // 没有音频，使用原有的延迟逻辑
                    if (System.currentTimeMillis() - lastCharTime > ClientConfig.AUTO_ADVANCE_DELAY.get()) {
                        canAutoAdvance = true;
                    }
                }

                if (canAutoAdvance) {
                    // 停止当前音频（如果有）
                    DialogManager.stopCurrentAudio();

                    DialogManager.getInstance().showNextDialog();
                    // 执行当前对话条目的指令
                    if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                        DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommands());
                    }
                    return;
                }
            }

            List<net.minecraft.util.FormattedCharSequence> lines;
            if (textFullyDisplayed) {
                lines = font.split(text, maxWidth);
            } else {
                var animatedString = DialogManager.subText(text, Math.min(currentCharIndex, rawText.length()));
                lines = font.split(animatedString, maxWidth);
            }

            for (net.minecraft.util.FormattedCharSequence line : lines) {
                guiGraphics.drawString(font, line, textX, textY, ClientConfig.DIALOG_TEXT_COLOR.get());
                textY += font.lineHeight;
            }
        }

        // 在文本完全显示后，并且有选项时，才创建和显示选项按钮
        if (textFullyDisplayed && dialogEntry.hasOptions()) {
            if (!this.optionButtonsCreated) {
                createOptionButtons();
                this.optionButtonsCreated = true;
            }
        }
        // 渲染按钮和其他UI元素
        for (Renderable renderable : this.renderables) {
            renderable.render(guiGraphics, mouseX, mouseY, partialTicks);
        }

        // 悬浮文本提示
        if (this.viewHistoryButton.isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("dialog.ui.history"), mouseX, mouseY);
        }
        if (this.autoPlayButton.isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("dialog.ui.auto_play"), mouseX, mouseY);
        }

        // 处理快速跳过
        boolean isCtrlPressed = Minecraft.getInstance().getWindow() != null &&
                (GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                        GLFW.glfwGetKey(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS);

        // 如果按下Ctrl键快速跳过，则关闭自动播放
        if (isCtrlPressed && DialogManager.isAutoPlaying()) {
            DialogManager.stopAutoPlay();
            updateAutoPlayButtonText();
        }

        if (isCtrlPressed && !dialogEntry.hasOptions()) {
            // 检查服务端配置和对话条目配置是否允许跳过
            boolean serverAllowsSkip = ServerConfig.ALLOW_SKIP_DIALOG.get();
            boolean entryAllowsSkip = dialogEntry.isSkipAllowed();

            if (serverAllowsSkip && entryAllowsSkip) {
                if (fastForwardCooldown > 0) {
                    fastForwardCooldown--;
                } else {
                    DialogManager.setFastForwardingNext(true);
                    // 停止当前音频播放（快进时不播放音频）
                    DialogManager.stopCurrentAudio();

                    // 执行当前对话条目的指令（如果存在）
                    if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                        DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommands(), this.speakerEntity);
                    }
                    DialogManager.getInstance().showNextDialog();
                    // 立即跳到下一条，避免渲染当前帧的剩余部分
                }
            }
        } else {
            // 如果Ctrl未按下或有选项，则清除快速跳过标记，确保正常流程
            DialogManager.setFastForwardingNext(false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return ClientConfig.IS_PAUSE_SCREEN.get();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 首先处理ESC键的特定行为
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.showingHistory) {
                // 如果在历史记录界面，ESC键返回对话界面
                toggleHistoryScreen();
                return true; // 事件已处理
            } else {
                // 检查对话序列是否允许关闭
                if (!dialogSequence.isCloseAllowed()) {
                    // 不允许关闭，直接返回，不处理ESC键
                    return true; // 事件已处理，但不执行关闭操作
                } else {
                    // 允许关闭，显示确认窗口
                    Component confirmMessage = Component.translatable("dialog.ui.confirm_esc");
                    ConfirmScreen confirmScreen = new ConfirmScreen(
                            this::confirmCloseDialogWithSkip,
                            Component.translatable("dialog.ui.esc"),
                            confirmMessage
                    );
                    this.minecraft.setScreen(confirmScreen);
                    return true; // 事件已处理
                }
            }
        }

        // 处理其他键的通用行为
        if (this.showingHistory) {
            return false;
        }

        // 当文本完全显示，且没有选项时，按空格键可以手动前进
        if (textFullyDisplayed && !dialogEntry.hasOptions() && (keyCode == GLFW.GLFW_KEY_SPACE)) {
            if (DialogManager.isAutoPlaying()) {
                DialogManager.stopAutoPlay();
                updateAutoPlayButtonText();
            }
            // 停止当前音频播放
            DialogManager.stopCurrentAudio();

            if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommands(), this.speakerEntity);
            }
            DialogManager.getInstance().showNextDialog();
            return true;
        }

        // 如果文本未完全显示，按空格则立即显示全部文本
        if (!textFullyDisplayed && (keyCode == GLFW.GLFW_KEY_SPACE)) {
            if (DialogManager.isAutoPlaying()) {
                DialogManager.stopAutoPlay();
                updateAutoPlayButtonText();
            }
            // 停止当前音频播放
            DialogManager.stopCurrentAudio();

            textFullyDisplayed = true;
            currentCharIndex = dialogEntry.getText(levelRegistryAccess(), playerName).getString().length();
            lastCharTime = System.currentTimeMillis();
            return true;
        }

        // 对于其他未处理的按键，调用父类的处理方法
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    //处理跳过对话确认的回调方法
    private void confirmCloseDialogWithSkip(boolean confirmed) {
        if (confirmed) {
            // 用户确认跳过，执行后续指令并关闭对话
            executeRemainingCommandsAndClose();
        } else {
            // 如果用户选择"否"，则重新显示当前对话界面
            if (this.minecraft != null) {
                this.minecraft.setScreen(this);
            }
        }
    }

    /**
     * 执行后续所有指令并关闭对话
     */
    private void executeRemainingCommandsAndClose() {
        // 获取当前条目之后的所有条目
        List<DialogEntry> remainingEntries = dialogSequence.getRemainingEntries(dialogEntry);

        // 执行所有后续条目中的指令
        for (DialogEntry entry : remainingEntries) {
            if (entry.getCommands() != null && !entry.getCommands().isEmpty()) {
                DialogManager.getInstance().executeCommands(this.getMinecraft().player, entry.getCommands(), this.speakerEntity);
            }
        }

        // 关闭对话界面
        this.onClose();
    }

    @Override
    public void onClose() {
        if (backgroundImageDisplayData != null && !isClosing) {
            // 开始淡出动画
            isClosing = true;
            backgroundFadeOutStartTime = System.currentTimeMillis();
            // 延迟关闭，等待淡出动画完成
            new Thread(() -> {
                try {
                    Thread.sleep(DialogManager.BACKGROUND_FADE_DURATION_MS);
                    minecraft.execute(super::onClose);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    minecraft.execute(super::onClose);
                }
            }).start();
        } else {
            super.onClose(); // 调用父类的onClose，确保屏幕正常关闭
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 如果点击，则关闭自动播放
        if (DialogManager.isAutoPlaying()) {
            DialogManager.stopAutoPlay();
            updateAutoPlayButtonText();
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        // 如果显示历史记录，检查是否点击了音频播放按钮
        if (showingHistory) {
            for (HistoryAudioButton audioButton : historyAudioButtons) {
                if (audioButton.isMouseOver(mouseX, mouseY) && button == 0) {
                    playHistoryAudio(audioButton.entry);
                    return true;
                }
            }
        }

        // 如果没有显示历史记录且没有 widget 处理点击事件，
        // 则检查是否点击了对话框区域以推进文本/对话。
        if (!showingHistory) {
            // 检查点击是否在对话框边界内
            boolean clickedInDialogBox = button == 0 &&
                    dialogBoxX <= mouseX && mouseX <= dialogBoxX + dialogBoxWidth &&
                    dialogBoxY <= mouseY && mouseY <= dialogBoxY + dialogBoxHeight;

            if (clickedInDialogBox) {
                if (!textFullyDisplayed) {
                    // 如果文本未完全显示，点击使其完全显示
                    textFullyDisplayed = true;
                    currentCharIndex = dialogEntry.getText(levelRegistryAccess(), playerName).getString().length();
                    lastCharTime = 0; // 重置动画或自动播放的时间
                    return true; // 消费点击事件
                } else {
                    // 文本已完全显示
                    if (!dialogEntry.hasOptions()) {
                        // 如果没有选项，则推进对话
                        // 执行当前对话条目的指令（如果存在）
                        if (dialogEntry.getCommands() != null && !dialogEntry.getCommands().isEmpty()) {
                            DialogManager.getInstance().executeCommands(this.getMinecraft().player, dialogEntry.getCommands(), this.speakerEntity);
                        }
                        DialogManager.getInstance().showNextDialog();
                        return true; // 消费点击事件
                    }
                }
            }
        }

        return false; // 除了 widgets 或对话推进之外没有自定义处理
    }

    /**
     * 切换对话历史记录界面的显示状态
     */
    public void toggleHistoryScreen() {
        this.showingHistory = !this.showingHistory;
    }

    private void toggleAutoPlay() {
        DialogManager.setAutoPlaying(!DialogManager.isAutoPlaying());
        updateAutoPlayButtonText();
    }

    private void updateAutoPlayButtonText() {
        if (this.autoPlayButton != null) {
            this.autoPlayButton.setMessage(Component.literal(DialogManager.isAutoPlaying() ? "⏸" : "▶"));
        }
    }

    /**
     * 播放历史记录中的音频
     */
    private void playHistoryAudio(DialogEntry entry) {
        // 停止当前播放的历史记录音频
        if (currentHistoryAudio != null) {
            Minecraft.getInstance().getSoundManager().stop(currentHistoryAudio);
            currentHistoryAudio = null;
        }

        // 播放新的音频
        if (entry.getAudioPath() != null && !entry.getAudioPath().isEmpty()) {
            try {
                // 移除.ogg后缀（如果存在）
                String soundName = entry.getAudioPath().replace(".ogg", "");

                // 构建音频资源位置 - 使用sounds.json中定义的音频事件名称
                ResourceLocation audioLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, soundName);

                // 创建音频实例并播放（与DialogManager中的方式保持一致）
                currentHistoryAudio = SimpleSoundInstance.forUI(
                        SoundEvent.createVariableRangeEvent(audioLocation),
                        1.0f, // volume
                        1.0f  // pitch
                );

                Minecraft.getInstance().getSoundManager().play(currentHistoryAudio);

            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to play history audio: " + entry.getAudioPath(), e);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        updateAutoPlayButtonText();

        if (this.showingHistory) {
            this.historyEntries = DialogManager.getInstance().getDialogHistory();
            // 禁用主对话界面按钮
            this.optionButtons.forEach(b -> b.active = false);
            if (this.viewHistoryButton != null) { // 确保按钮已初始化
                this.viewHistoryButton.active = false;
            }

            // 激活并添加关闭历史按钮
            if (!this.children().contains(this.closeHistoryButton)) {
                this.addRenderableWidget(this.closeHistoryButton);
            }
            this.closeHistoryButton.active = true;

        } else {
            // 恢复主对话界面按钮
            this.optionButtons.forEach(b -> b.active = true);
            if (this.viewHistoryButton != null) { // 确保按钮已初始化
                this.viewHistoryButton.active = true;
            }

            // 移除关闭历史按钮
            if (this.children().contains(this.closeHistoryButton)) {
                this.removeWidget(this.closeHistoryButton);
            }
            this.closeHistoryButton.active = false;
        }
    }

    /**
     * 渲染对话历史记录界面
     */
    private void renderHistoryScreen(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {

        //悬浮文本提示
        if (this.closeHistoryButton.isMouseOver(mouseX, mouseY)) {
            guiGraphics.renderTooltip(this.font, Component.translatable("dialog.ui.close_history"), mouseX, mouseY);
        }

        // 渲染背景
        guiGraphics.fill(0, 0, this.width, this.height, 0xCC000000); // 半透明黑色背景

        int currentY = (int) (this.height * 0.1);
        final int textPaddingLeft = 50;
        final int optionPaddingLeft = textPaddingLeft + 5;
        final int extraEmptyLineHeight = font.lineHeight;
        final int historyAreaTopY = (int) (this.height * 0.1);
        final int historyAreaBottomY = this.height - 40; // 底部留出空间给关闭按钮等
        final int historyAreaHeight = historyAreaBottomY - historyAreaTopY;

        // 计算最大宽度
        final int dialogTextMaxWidth = Math.max(1, this.width - textPaddingLeft - 20 - 15); // 20 右缩进, 15 为滚动条宽度和间距
        final int optionTextMaxWidth = Math.max(1, this.width - optionPaddingLeft - 20 - 15);

        // 重新计算内容总高度
        totalHistoryContentHeight = 0;
        for (DialogEntry entry : historyEntries) {
            Component currentEntrySpeaker = entry.getSpeaker(levelRegistryAccess(), playerName);
            Component dialogText = entry.getText(levelRegistryAccess(), playerName);
            Component lineToRender;
            if (dialogText == null) dialogText = Component.empty();
            if (currentEntrySpeaker != null && !currentEntrySpeaker.getString().isEmpty()) {
                lineToRender = Component.literal("[").append(currentEntrySpeaker).append("] ").append(dialogText);
            } else {
                lineToRender = dialogText;
            }
            if (lineToRender != null) {
                List<net.minecraft.util.FormattedCharSequence> wrappedDialogLines = font.split(lineToRender, dialogTextMaxWidth);
                if (wrappedDialogLines.isEmpty() && !lineToRender.getString().isEmpty()) {
                    totalHistoryContentHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedDialogLines) {
                        totalHistoryContentHeight += font.lineHeight + 2;
                    }
                }
            } else {
                totalHistoryContentHeight += font.lineHeight + 2;
            }
            totalHistoryContentHeight += 5; // 条目间距
            if (entry.getSelectedOptionText() != null && !entry.getSelectedOptionText().isEmpty()) {
                Component optionComponent = Component.literal(" -> " + entry.getSelectedOptionText());
                List<net.minecraft.util.FormattedCharSequence> wrappedOptionLines = font.split(optionComponent, optionTextMaxWidth);
                if (wrappedOptionLines.isEmpty() && !optionComponent.getString().isEmpty()) {
                    totalHistoryContentHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedOptionLines) {
                        totalHistoryContentHeight += font.lineHeight + 2;
                    }
                }
                totalHistoryContentHeight += extraEmptyLineHeight; // 选项后间距
            }
        }

        // 渲染实际可见内容
        currentY = historyAreaTopY - historyScrollOffset; // 应用滚动偏移

        // 清空之前的音频按钮
        historyAudioButtons.clear();

        int entryIndex = 0;
        for (DialogEntry entry : historyEntries) {


            Component currentEntrySpeaker = entry.getSpeaker(levelRegistryAccess(), playerName);
            Component dialogText = entry.getText(levelRegistryAccess(), playerName);
            Component lineToRender;

            if (dialogText == null) {
                dialogText = Component.empty();
            }

            if (currentEntrySpeaker != null && !currentEntrySpeaker.getString().isEmpty()) {
                lineToRender = Component.literal("[").append(currentEntrySpeaker).append("] ").append(dialogText);
            } else {
                lineToRender = dialogText;
            }

            int entryStartY = currentY;
            int entryHeight = 0;

            if (lineToRender != null) {
                List<net.minecraft.util.FormattedCharSequence> wrappedDialogLines = font.split(lineToRender, dialogTextMaxWidth);
                if (wrappedDialogLines.isEmpty() && !lineToRender.getString().isEmpty()) {
                    if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                        guiGraphics.drawString(font, lineToRender, textPaddingLeft, currentY, 0xFFFFFF);
                    }
                    currentY += font.lineHeight + 2;
                    entryHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedDialogLines) {
                        if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                            guiGraphics.drawString(font, line, textPaddingLeft, currentY, 0xFFFFFF);
                        }
                        currentY += font.lineHeight + 2;
                        entryHeight += font.lineHeight + 2;
                    }
                }
            } else {
                currentY += font.lineHeight + 2;
                entryHeight += font.lineHeight + 2;
            }
            currentY += 5;
            entryHeight += 5;

            // 显示选择的选项
            if (entry.getSelectedOptionText() != null && !entry.getSelectedOptionText().isEmpty()) {
                Component optionComponent = Component.literal(" -> " + entry.getSelectedOptionText());
                currentY += 5;
                entryHeight += 5;

                List<net.minecraft.util.FormattedCharSequence> wrappedOptionLines = font.split(optionComponent, optionTextMaxWidth);
                if (wrappedOptionLines.isEmpty() && !optionComponent.getString().isEmpty()) {
                    if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                        guiGraphics.drawString(font, optionComponent, optionPaddingLeft, currentY, 0xAAAAAA);
                    }
                    currentY += font.lineHeight + 2;
                    entryHeight += font.lineHeight + 2;
                } else {
                    for (net.minecraft.util.FormattedCharSequence line : wrappedOptionLines) {
                        if (currentY + font.lineHeight > historyAreaTopY && currentY < historyAreaBottomY) {
                            guiGraphics.drawString(font, line, optionPaddingLeft, currentY, 0xAAAAAA);
                        }
                        currentY += font.lineHeight + 2;
                        entryHeight += font.lineHeight + 2;
                    }
                }
                currentY += extraEmptyLineHeight;
                entryHeight += extraEmptyLineHeight;
            }
            // 如果条目的任何部分在可视区域之上，并且其结束部分在可视区域之下，则认为该条目是（部分）可见的

            // 如果条目有音频配置，创建播放按钮
            if (entry.getAudioPath() != null && !entry.getAudioPath().isEmpty()) {
                int buttonSize = 12;
                int buttonX = textPaddingLeft - buttonSize - 5; // 在文本左侧
                int buttonY = entryStartY + (entryHeight - buttonSize) / 2 - 4; // 垂直居中

                // 只有当按钮在可视区域内时才添加
                if (buttonY + buttonSize > historyAreaTopY && buttonY < historyAreaBottomY) {
                    historyAudioButtons.add(new HistoryAudioButton(entry, buttonX, buttonY, buttonSize, buttonSize, entryIndex));
                }
            }

            entryIndex++;
        }

        // 渲染音频播放按钮
        for (HistoryAudioButton button : historyAudioButtons) {
            // 绘制按钮背景
            int buttonColor = button.isMouseOver(mouseX, mouseY) ? 0xFF555555 : 0xFF333333;
            guiGraphics.fill(button.x, button.y, button.x + button.width, button.y + button.height, buttonColor);

            // 绘制播放图标
            String playIcon = "🔈";
            int iconX = button.x + (button.width - font.width(playIcon)) / 2;
            int iconY = button.y + (button.height - font.lineHeight) / 2;
            guiGraphics.drawString(font, playIcon, iconX, iconY, 0xFFFFFF);
        }

        // 更新滚动状态
        canScrollHistoryUp = historyScrollOffset > 0;
        canScrollHistoryDown = totalHistoryContentHeight > historyAreaHeight && historyScrollOffset < (totalHistoryContentHeight - historyAreaHeight);

        // 渲染滚动提示箭头 (向下)
        if (canScrollHistoryDown) {
            int arrowX = this.width / 2;
            int arrowY = historyAreaBottomY + 5; // 在历史区域下方
            guiGraphics.drawString(font, "▼", arrowX - font.width("▼") / 2, arrowY, 0xFFFFFF);
        }
        // 渲染滚动提示箭头 (向上)
        if (canScrollHistoryUp) {
            int arrowX = this.width / 2;
            int arrowY = historyAreaTopY - font.lineHeight - 5; // 在历史区域上方
            guiGraphics.drawString(font, "▲", arrowX - font.width("▲") / 2, arrowY, 0xFFFFFF);
        }

        // 渲染滚动条
        if (totalHistoryContentHeight > historyAreaHeight) {
            int scrollbarWidth = 5;
            int scrollbarX = this.width - textPaddingLeft + 20; // 调整到文本区域右侧
            int scrollbarTrackHeight = historyAreaHeight;

            // 滚动条背景
            guiGraphics.fill(scrollbarX, historyAreaTopY, scrollbarX + scrollbarWidth, historyAreaTopY + scrollbarTrackHeight, 0xFF555555);

            float scrollPercentage = (float) historyScrollOffset / (totalHistoryContentHeight - historyAreaHeight);
            int scrollThumbHeight = Math.max(20, (int) ((float) historyAreaHeight / totalHistoryContentHeight * historyAreaHeight));
            int scrollThumbY = historyAreaTopY + (int) (scrollPercentage * (scrollbarTrackHeight - scrollThumbHeight));

            guiGraphics.fill(scrollbarX, scrollThumbY, scrollbarX + scrollbarWidth, scrollThumbY + scrollThumbHeight, 0xFFAAAAAA);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.showingHistory) {
            int scrollAmount = (int) (-scrollY * (font.lineHeight + 2) * 2); // 每次滚动2行的高度
            int newScrollOffset = this.historyScrollOffset + scrollAmount;
            int maxScroll = Math.max(0, totalHistoryContentHeight - (this.height - 40 - (int) (this.height * 0.1)));

            this.historyScrollOffset = Mth.clamp(newScrollOffset, 0, maxScroll);
            return true;
        } else {
            // 在对话界面中，向上滚动（scrollY > 0）打开历史记录界面
            if (scrollY > 0) {
                toggleHistoryScreen();
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // 历史记录音频播放按钮类
    private static class HistoryAudioButton {
        public final DialogEntry entry;
        public final int x, y, width, height;
        public final int entryIndex;

        public HistoryAudioButton(DialogEntry entry, int x, int y, int width, int height, int entryIndex) {
            this.entry = entry;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.entryIndex = entryIndex;
        }

        public boolean isMouseOver(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}