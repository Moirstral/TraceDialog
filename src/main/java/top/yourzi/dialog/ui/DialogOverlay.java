package top.yourzi.dialog.ui;

import lombok.Getter;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import org.jetbrains.annotations.NotNull;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;
import top.yourzi.dialog.config.ClientConfig;
import top.yourzi.dialog.model.BackgroundAnimationType;
import top.yourzi.dialog.model.DialogEntry;
import top.yourzi.dialog.model.DialogSequence;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = Dialog.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class DialogOverlay implements LayeredDraw.Layer {
    @Getter
    private static DialogOverlay instance;
    private final Minecraft minecraft;
    private final Font font;

    // 对话序列和当前对话条目
    private DialogSequence dialogSequence;
    private DialogEntry dialogEntry;
    // 玩家名称
    private String playerName;
    //立绘数据列表
    private final List<PortraitDisplayData> portraitDisplayList = new ArrayList<>();
    // 需要在对话中显示的物品列表
    private final List<ItemStack> displayItemStacks = new ArrayList<>();
    // 背景图片相关
    private BackgroundImageDisplayData backgroundImageDisplayData;
    private long backgroundFadeOutStartTime = 0; // 背景图片淡出开始时间
    private boolean isClosing = false; // 是否正在关闭
    // 对话框背景图片
    private String dialogBackgroundImagePath;
    // 文本动画相关
    private int currentCharIndex = 0;
    private long lastCharTime = 0;
    private boolean textFullyDisplayed = false;

    private final List<Component> history = new ArrayList<>();

    public DialogOverlay(Minecraft minecraft) {
        this.minecraft = minecraft;
        this.font = Minecraft.getInstance().font;
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        // 注册对话层
        instance = new DialogOverlay(Minecraft.getInstance());
        event.registerAboveAll(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "dialog_overlay"), instance);
    }

    public void setDialogEntry(DialogSequence dialogSequence, DialogEntry dialogEntry) {
        this.clear();
        if (minecraft.player != null) {
            this.playerName = minecraft.player.getDisplayName().getString();
        }
        this.isClosing = false;

        this.dialogSequence = dialogSequence;
        this.dialogEntry = dialogEntry;
        long currentTime = System.currentTimeMillis();
        // 加载背景图片资源
        if (dialogEntry.getBackgroundImage() != null && dialogEntry.getBackgroundImage().getPath() != null && !dialogEntry.getBackgroundImage().getPath().isEmpty()) {
            this.backgroundImageDisplayData = new BackgroundImageDisplayData(dialogEntry.getBackgroundImage());
            // 根据动画类型设置动画开始时间
            if (this.backgroundImageDisplayData.animationType == BackgroundAnimationType.FADE_IN) {
                this.backgroundImageDisplayData.animationStartTime = currentTime;
            }
        }

        if (dialogEntry.getDialogImage() != null && !dialogEntry.getDialogImage().isEmpty()) {
            this.dialogBackgroundImagePath = dialogEntry.getDialogImage();
        } else {
            this.dialogBackgroundImagePath = null;
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
                        if (item != Items.AIR) {
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

        // 如果当前对话条目有音频路径且不是快进模式，则开始播放音频
        if (dialogEntry.getAudioPath() != null && !dialogEntry.getAudioPath().isEmpty()) {
            DialogManager.playDialogAudio(dialogEntry.getAudioPath());
        }
    }


    public void close() {
        if (backgroundImageDisplayData != null && !isClosing) {
            // 开始淡出动画
            isClosing = true;
            backgroundFadeOutStartTime = System.currentTimeMillis();
            // 延迟关闭，等待淡出动画完成
            new Thread(() -> {
                try {
                    Thread.sleep(DialogManager.BACKGROUND_FADE_DURATION_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
        String title = dialogSequence == null ? "" : dialogSequence.getTitle();
        this.clear();
        if (!this.history.isEmpty() && ClientConfig.SHOW_HISTORY_IN_CHAT.get() && this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("dialog.chat.history.title", title), false);
            for (Component component : this.history) {
                this.minecraft.player.displayClientMessage(component, false);
            }
            this.minecraft.player.displayClientMessage(Component.translatable("dialog.chat.history.footer", title), false);
        }
        this.history.clear();
    }

    public void clear() {
        if (this.dialogEntry != null) {
            Component text = dialogEntry.getText(levelRegistryAccess(), playerName);
            Component speaker = dialogEntry.getSpeaker(levelRegistryAccess(), playerName);
            this.history.add(Component.translatable("dialog.chat.history.entry", speaker, text));
        }
        this.dialogSequence = null;
        this.dialogEntry = null;
        this.portraitDisplayList.clear();
        this.displayItemStacks.clear();
        this.backgroundImageDisplayData = null;
        this.lastCharTime = 0;
        this.currentCharIndex = 0;
        this.textFullyDisplayed = false;
        DialogManager.stopCurrentAudio();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, @NotNull DeltaTracker tracker) {
        if (dialogEntry == null) {
            clear();
            return;
        }
        if (this.minecraft.player != null && this.minecraft.player.isDeadOrDying()) {
            // 玩家死亡，停止渲染，复活后会继续
            return;
        }
        if (this.minecraft.isPaused() || (this.minecraft.screen != null && this.minecraft.screen.isPauseScreen())) {
            // 暂停时，不渲染
            return;
        }
        this.minecraft.getProfiler().push("trace_dialog");

        int guiWidth = guiGraphics.guiWidth();
        int guiHeight = guiGraphics.guiHeight();

        // 设置对话框位置和大小
        int dialogBoxWidth = ClientConfig.OVERLAY_DIALOG_BOX_WIDTH.get();
        int dialogBoxHeight = ClientConfig.OVERLAY_DIALOG_BOX_HEIGHT.get();
        int dialogBoxOffset = ClientConfig.OVERLAY_DIALOG_BOX_OFFSETY.get();
        int dialogBoxX = (guiWidth - dialogBoxWidth) / 2;
        int dialogBoxY = guiHeight - dialogBoxHeight - dialogBoxOffset;

        // 首先渲染背景图片 (如果存在且加载成功)
        if (this.backgroundImageDisplayData != null && this.backgroundImageDisplayData.loadedSuccessfully) {
            DialogManager.renderBackgroundImage(guiGraphics, this.backgroundImageDisplayData, isClosing, backgroundFadeOutStartTime, guiWidth, guiHeight);
        }

        // 渲染立绘
        if (!portraitDisplayList.isEmpty()) {
            DialogManager.renderPortrait(guiGraphics, portraitDisplayList, guiWidth, guiHeight, 0.3f, dialogBoxX, 0);
        }

        // 渲染对话框背景
        DialogManager.renderDialogBackground(guiGraphics, dialogBackgroundImagePath, dialogBoxX, dialogBoxY, dialogBoxWidth, dialogBoxHeight);

        // 渲染对话文本
        int padding = ClientConfig.OVERLAY_DIALOG_BOX_PADDING.get();
        int textX = dialogBoxX + padding;
        int textY = dialogBoxY + padding;

        // 如果显示说话者名称且有说话者
        Component speakerComponent = dialogEntry.getSpeaker(levelRegistryAccess(), playerName);
        if (ClientConfig.SHOW_SPEAKER_NAME.get() && speakerComponent != null && !speakerComponent.getString().isEmpty()) {
            guiGraphics.drawString(font, speakerComponent, textX, dialogBoxY - font.lineHeight - 2, 0xFFFFFF);
        }

        // 渲染对话文本
        Component text = dialogEntry.getText(levelRegistryAccess(), playerName);
        String rawText = text.getString();
        long currentTime = System.currentTimeMillis();
        if (!rawText.isEmpty()) {
            int maxWidth = dialogBoxWidth - (padding * 2);
            int textAnimationSpeed = ClientConfig.TEXT_ANIMATION_SPEED.get(); // 每秒字符数

            if (textAnimationSpeed <= 0) { // 立即显示
                textFullyDisplayed = true;
                currentCharIndex = rawText.length();
            }

            if (!textFullyDisplayed) {
                if (lastCharTime <= 0) { // 首次渲染或重置
                    lastCharTime = currentTime;
                }
                // 计算每字符间隔时间 (毫秒)
                long charInterval = 1000 / textAnimationSpeed;

                if (currentTime - lastCharTime >= charInterval) {
                    currentCharIndex++;
                    lastCharTime = currentTime;
                    if (currentCharIndex >= rawText.length()) {
                        textFullyDisplayed = true;
                        currentCharIndex = rawText.length(); // 确保索引不超过长度
                    }
                }
            }

            // 如果文本完全显示，则延迟后自动前进
            if (textFullyDisplayed) {
                boolean canAutoAdvance = false;

                // 如果当前对话有音频，等待音频播放完毕再跳转
                if (dialogEntry.getAudioPath() != null && !dialogEntry.getAudioPath().isEmpty()) {
                    if (DialogManager.isAudioFinished()) {
                        canAutoAdvance = true;
                    }
                } else {
                    // 没有音频，使用原有的延迟逻辑
                    if (currentTime - lastCharTime > ClientConfig.AUTO_ADVANCE_DELAY.get()) {
                        canAutoAdvance = true;
                    }
                }

                if (canAutoAdvance) {
                    // 停止当前音频（如果有）
                    DialogManager.stopCurrentAudio();

                    // 执行当前对话条目的指令
                    if (dialogEntry.getCommand() != null && !dialogEntry.getCommand().isEmpty()) {
                        DialogManager.getInstance().executeCommands(minecraft.player, dialogEntry.getCommands());
                    }
                    DialogManager.getInstance().showNextDialogOverlay();
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
            int maxHeight = dialogBoxHeight - (padding * 2);
            int lineHeight = font.lineHeight + 2;
            for (int i = Math.max(0, lines.size() - (maxHeight / lineHeight)); i < lines.size(); i++) {
                guiGraphics.drawString(font, lines.get(i), textX, textY, ClientConfig.DIALOG_TEXT_COLOR.get());
                textY += lineHeight;
            }
        }

        DialogManager.renderDisplayItem(guiGraphics, font, this.displayItemStacks, dialogBoxWidth, dialogBoxX, dialogBoxY, 0, 0);

        // 渲染对话中展示的物品
        if (!this.displayItemStacks.isEmpty()) {
            int itemSize = 16;
            int itemPadding = 4;
            int totalItemWidth = (this.displayItemStacks.size() * itemSize) + (Math.max(0, this.displayItemStacks.size() - 1) * itemPadding);

            int startX = dialogBoxX + (dialogBoxWidth - totalItemWidth) / 2;
            int itemY = dialogBoxY - itemSize - 5;

            for (ItemStack itemStack : this.displayItemStacks) {
                guiGraphics.renderItem(itemStack, startX, itemY);
                guiGraphics.renderItemDecorations(this.font, itemStack, startX, itemY);
                startX += itemSize + itemPadding;
            }
        }
        this.minecraft.getProfiler().pop();
    }

    private HolderLookup.Provider levelRegistryAccess() {
        if (Minecraft.getInstance().level != null) {
            return Minecraft.getInstance().level.registryAccess();
        }
        return null;
    }
}
