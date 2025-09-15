package top.yourzi.dialog;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import top.yourzi.dialog.config.ClientConfig;
import top.yourzi.dialog.model.*;
import top.yourzi.dialog.network.NetworkHandler;
import top.yourzi.dialog.ui.BackgroundImageDisplayData;
import top.yourzi.dialog.ui.DialogOverlay;
import top.yourzi.dialog.ui.DialogScreen;
import top.yourzi.dialog.ui.PortraitDisplayData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DialogManager {
    public static final Gson GSON = new GsonBuilder().create();
    private static final DialogManager INSTANCE = new DialogManager();

    // 服务端: 存储所有从数据包加载的对话序列
    // 客户端: 存储从服务端同步过来的对话序列
    private final Map<String, DialogSequence> dialogSequences = new HashMap<>();
    // 当前显示的对话序列
    private DialogSequence currentSequence;
    // 当前等待显示的对话序列
    private final LinkedBlockingQueue<WaitingDialogSequence> waitingDialogSequences = new LinkedBlockingQueue<>();
    private static Thread pollingThread;
    // 当前显示的对话条目
    private DialogEntry currentEntry;
    // 对话历史记录
    private final List<DialogEntry> dialogHistory = new ArrayList<>();
    // 标记下一次对话推进是否由快速跳过触发
    private static boolean isFastForwardingNext = false;

    // 全局音频播放管理
    private static SimpleSoundInstance currentAudioInstance = null;
    private static long audioStartTime = 0;
    private static long audioEndTime = 0;
    private static boolean audioPlaying = false;
    // 自动播放状态
    private static boolean isAutoPlaying = false;
    // 存储当前对话的玩家名称
    private String currentDialogPlayerName;

    public static final int BACKGROUND_FADE_DURATION_MS = 500; // 背景图片淡入淡出持续时间，单位毫秒
    public static final int ANIMATION_DURATION_MS = 300; // 动画持续时间，单位毫秒

    private DialogManager() {
    }

    protected record WaitingDialogSequence(DialogSequence dialogSequence, Integer speakerEntityId) {
        WaitingDialogSequence(DialogSequence dialogSequence) {
            this(dialogSequence, null);
        }
    }

    /**
     * 向玩家发送消息。
     */
    @OnlyIn(Dist.CLIENT)
    private void sendPlayerMessage(Component message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.sendSystemMessage(message);
        }
    }

    public static DialogManager getInstance() {
        return INSTANCE;
    }

    /**
     * 加载所有对话序列 (仅服务端调用)。
     * 此方法从数据包 (data/<modid>/dialogs/) 加载对话。
     *
     * @param resourceManager 资源管理器实例。
     */
    public void loadDialogsFromServer(ResourceManager resourceManager) {
        dialogSequences.clear();

        Map<ResourceLocation, Resource> modSpecificResources = resourceManager.listResources("dialogs", resource -> resource.getPath().endsWith(".json")).entrySet().stream()
                .filter(entry -> entry.getKey().getNamespace().equals(Dialog.MODID))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));


        modSpecificResources.forEach((resourceLocation, resource) -> {
            try {
                DialogSequence sequence = parseDialogSequenceFromFile(resource); // 解析对话序列
                if (sequence != null && sequence.getId() != null) {
                    dialogSequences.put(sequence.getId(), sequence);
                } else {
                    Dialog.LOGGER.warn("Empty dialog sequence or empty ID. {}", resourceLocation);
                }
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to load dialog file {}: {}", resourceLocation, e.getMessage(), e);
            }
        });

        if (dialogSequences.isEmpty()) {
            Dialog.LOGGER.warn("No dialog sequence was found, please check the 'dialogs' directory ('data/{}/dialogs') or file format in the datapack.", Dialog.MODID);
        }
    }

    /**
     * 解析对话序列JSON文件 (内部使用, 服务端加载时调用)。
     *
     * @param resource 资源文件。
     */
    private DialogSequence parseDialogSequenceFromFile(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
            return GSON.fromJson(reader, DialogSequence.class);
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            Dialog.LOGGER.error("Failure to read or parse dialog JSON file ({}): {}", resource.sourcePackId(), e.getMessage());
            // 尝试读取内容以进行更详细的调试
            try (BufferedReader contentReader = new BufferedReader(new InputStreamReader(resource.open(), StandardCharsets.UTF_8))) {
                StringBuilder jsonContent = new StringBuilder();
                String line;
                while ((line = contentReader.readLine()) != null) {
                    jsonContent.append(line);
                }
                Dialog.LOGGER.debug("JSON: {}", jsonContent);
            } catch (IOException ioe) {
                Dialog.LOGGER.error("Unable to read problematic JSON content for debugging. {}", ioe.getMessage());
            }
            return null;
        }
    }

    /**
     * (客户端) 清空所有已缓存的对话数据。
     * 通常在从服务器断开或服务器重载数据包时调用。
     */
    @OnlyIn(Dist.CLIENT)
    public void clearAllDialogsOnClient() {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;
        dialogSequences.clear();
        currentSequence = null;
        currentEntry = null;
        clearDialogHistory();
    }

    /**
     * (客户端) 接收并缓存从服务器同步过来的所有对话数据。
     *
     * @param dialogDataMap 一个映射，键是对话ID，值是对话内容的JSON字符串。
     */
    @OnlyIn(Dist.CLIENT)
    public void receiveAllDialogsFromServer(Map<String, String> dialogDataMap) {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;
        clearAllDialogsOnClient(); // 先清空旧数据
        dialogDataMap.forEach((id, json) -> {
            try {
                DialogSequence sequence = GSON.fromJson(json, DialogSequence.class);
                if (sequence != null && sequence.getId() != null) {
                    if (!id.equals(sequence.getId())) {
                        Dialog.LOGGER.warn("Dialog ID mismatch! Expected ID: {}, ID in JSON: {}. Will use expected ID.", id, sequence.getId());
                    }
                    dialogSequences.put(id, sequence); // 使用map的key作为权威ID
                    Dialog.LOGGER.debug("Client Successfully Cached Conversation. {}", id);
                } else {
                    Dialog.LOGGER.warn("Parsing of the dialog data received from the server failed or the ID is null. ID: {}, JSON: {}", id, json);
                }
            } catch (JsonSyntaxException e) {
                Dialog.LOGGER.error("Failed to parse the dialog JSON received from the server. ID: {}, 错误: {}", id, e.getMessage());
                Dialog.LOGGER.debug("(ID: {}): {}", id, json, e);
            }
        });
        if (dialogSequences.isEmpty() && !dialogDataMap.isEmpty()) {
            Dialog.LOGGER.warn("Dialog data has been received but the cache is empty after parsing, please check the JSON format and content.");
        }
    }

    /**
     * 将对话条目添加到历史记录。
     */
    @OnlyIn(Dist.CLIENT)
    private void addDialogToHistory(DialogEntry entry) {
        if (entry != null) {
            dialogHistory.add(entry);
        }
    }

    /**
     * 获取对话历史记录。
     */
    @OnlyIn(Dist.CLIENT)
    public List<DialogEntry> getDialogHistory() {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide)
            return Collections.emptyList();
        return new ArrayList<>(dialogHistory);
    }

    /**
     * 清空对话历史记录。
     */
    @OnlyIn(Dist.CLIENT)
    private void clearDialogHistory() {
        dialogHistory.clear();
    }

    /**
     * 记录玩家在当前对话中选择的选项。
     *
     * @param optionText 所选选项的文本。
     */
    @OnlyIn(Dist.CLIENT)
    public void recordChoiceForCurrentDialog(String optionText) {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;
        if (currentEntry != null) {
            currentEntry.setSelectedOptionText(optionText);
            // 更新历史记录中最新的对应条目
            if (!dialogHistory.isEmpty()) {
                DialogEntry lastHistoryEntry = dialogHistory.get(dialogHistory.size() - 1);
                // 确保更新的是同一个对话条目（理论上应该是同一个）
                if (lastHistoryEntry == currentEntry) {
                    lastHistoryEntry.setSelectedOptionText(optionText);
                } else {
                    // 如果不是同一个条目，可能存在逻辑错误，或者 currentEntry 在添加到历史记录后被更改。
                    // 尝试通过ID查找并更新。
                    for (int i = dialogHistory.size() - 1; i >= 0; i--) {
                        if (dialogHistory.get(i).getId() != null && dialogHistory.get(i).getId().equals(currentEntry.getId())) {
                            dialogHistory.get(i).setSelectedOptionText(optionText);
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * (服务端) 获取所有对话序列的JSON表示，用于发送给客户端。
     */
    public Map<String, String> getAllDialogJsonsForSync() {
        Map<String, String> dialogJsons = new HashMap<>();
        dialogSequences.forEach((id, sequence) -> {
            dialogJsons.put(id, GSON.toJson(sequence));
        });
        return dialogJsons;
    }

    /**
     * 根据ID获取对话序列。
     * 服务端：从加载的对话中获取。
     * 客户端：从缓存的对话中获取。
     */
    public DialogSequence getDialogSequence(String id) {
        DialogSequence original = dialogSequences.get(id);
        if (original != null) {
            return GSON.fromJson(GSON.toJson(original), DialogSequence.class);
        }
        return null;
    }

    /**
     * 获取所有对话序列。
     */
    public Map<String, DialogSequence> getAllDialogSequences() {
        return new HashMap<>(dialogSequences);
    }

    /**
     * Component 序列号到 JsonElement
     *
     * @param component
     * @param provider
     * @return
     */
    static JsonElement serialize(Component component, HolderLookup.Provider provider) {
        return ComponentSerialization.CODEC.encodeStart(provider.createSerializationContext(JsonOps.INSTANCE), component).getOrThrow(JsonParseException::new);
    }

    /**
     * (服务端) 为特定玩家创建一个对话序列的副本，并根据玩家权限和visibility_command过滤选项。
     *
     * @param originalSequence 原始对话序列。
     * @param player           执行命令的玩家。
     * @param server           Minecraft服务器实例。
     * @return 经过选项过滤的对话序列副本；如果原始序列为null，则返回null。
     */
    public DialogSequence createPlayerSpecificSequence(DialogSequence originalSequence, ServerPlayer player, MinecraftServer server) {
        if (originalSequence == null) {
            Dialog.LOGGER.warn("Attempted to create player-specific sequence from null originalSequence.");
            return null;
        }


        DialogSequence playerSpecificSequence = GSON.fromJson(GSON.toJson(originalSequence), DialogSequence.class);

        if (playerSpecificSequence == null) {
            Dialog.LOGGER.error("Failed to deep copy originalSequence for ID: {}. No player-specific sequence will be generated.", originalSequence.getId());
            return null;
        }

        if (playerSpecificSequence.getEntries() == null) {
            return playerSpecificSequence;
        }

        List<DialogEntry> visibleEntries = new ArrayList<>();
        CommandSourceStack commandSource = player.createCommandSourceStack()
                .withPermission(server.getOperatorUserPermissionLevel())
                .withSuppressedOutput();

        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        for (DialogEntry entry : playerSpecificSequence.getEntries()) {
            if (entry == null) {
                continue;
            }

            // 检查条目本身的可见性命令
            String entryVisibilityCommand = entry.getVisibilityCommand();
            if (entryVisibilityCommand != null && !entryVisibilityCommand.isEmpty()) {
                try {
                    int result = dispatcher.execute(dispatcher.parse(entryVisibilityCommand, commandSource));
                    if (result != 1) {
                        Dialog.LOGGER.debug("Visibility command '{}' for entry '{}' (dialog '{}') for player {} returned {}, entry hidden.",
                                entryVisibilityCommand, entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), result);
                        continue; // 跳过此条目，不添加到 visibleEntries
                    }
                } catch (Exception e) {
                    Dialog.LOGGER.warn("Error executing visibility command '{}' for entry '{}' (dialog '{}') for player {}: {}. Entry hidden.",
                            entryVisibilityCommand, entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage());
                    continue; // 出错则隐藏条目
                }
            }
            RegistryAccess levelRegistryAccess = Minecraft.getInstance().level.registryAccess();

            //解析条目文本和说话者中的选择器
            // commandSource 和 player 来自方法参数，在此作用域内可用
            if (entry.getText() != null) { // 假设 entry.getText() 返回 JsonElement
                try {
                    Component textComponent = Component.Serializer.fromJson(entry.getText(), levelRegistryAccess);
                    if (textComponent != null) {
                        Component resolvedTextComponent = ComponentUtils.updateForEntity(commandSource, textComponent, player, 0);
                        entry.setText(serialize(resolvedTextComponent, levelRegistryAccess));
                    }
                } catch (JsonSyntaxException e) {
                    Dialog.LOGGER.warn("Failed to parse text component JSON for entry '{}' (dialog '{}') for player {}: {}. Skipping text update.",
                            entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage());
                } catch (Exception e) {
                    Dialog.LOGGER.error("Unexpected error processing text component for entry '{}' (dialog '{}') for player {}: {}. Skipping text update.",
                            entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage(), e);
                }
            }

            if (entry.getSpeaker() != null) { // 假设 entry.getSpeaker() 返回 JsonElement
                try {
                    Component speakerComponent = Component.Serializer.fromJson(entry.getSpeaker(), levelRegistryAccess);
                    if (speakerComponent != null) {
                        Component resolvedSpeakerComponent = ComponentUtils.updateForEntity(commandSource, speakerComponent, player, 0);
                        entry.setSpeaker(serialize(resolvedSpeakerComponent, levelRegistryAccess));
                    }
                } catch (JsonSyntaxException e) {
                    Dialog.LOGGER.warn("Failed to parse speaker component JSON for entry '{}' (dialog '{}') for player {}: {}. Skipping speaker update.",
                            entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage());
                } catch (Exception e) {
                    Dialog.LOGGER.error("Unexpected error processing speaker component for entry '{}' (dialog '{}') for player {}: {}. Skipping speaker update.",
                            entry.getId(), playerSpecificSequence.getId(), player.getName().getString(), e.getMessage(), e);
                }
            }

            // 如果条目可见，再处理其选项的可见性
            if (entry.hasOptions()) {
                List<DialogOption> visibleOptions = new ArrayList<>();
                for (DialogOption option : entry.getOptions()) {
                    String optionVisibilityCommand = option.getVisibilityCommand();
                    if (optionVisibilityCommand == null || optionVisibilityCommand.isEmpty()) {
                        visibleOptions.add(option);
                        continue;
                    }

                    try {
                        int result = dispatcher.execute(dispatcher.parse(entryVisibilityCommand, commandSource));
                        if (result == 1) {
                            visibleOptions.add(option);
                        } else {
                            Dialog.LOGGER.debug("Visibility command '{}' for option '{}' (dialog '{}', entry '{}') for player {} returned {}, option hidden.",
                                    optionVisibilityCommand, option.getText(levelRegistryAccess, player.getName().getString()) != null ? option.getText(levelRegistryAccess, player.getName().getString()).getString() : "<no text>", playerSpecificSequence.getId(), entry.getId(), player.getName().getString(), result);
                        }
                    } catch (Exception e) {
                        Dialog.LOGGER.warn("Error executing visibility command '{}' for option '{}' (dialog '{}', entry '{}') for player {}: {}. Option hidden.",
                                optionVisibilityCommand, option.getText(levelRegistryAccess, player.getName().getString()) != null ? option.getText(levelRegistryAccess, player.getName().getString()).getString() : "<no text>", playerSpecificSequence.getId(), entry.getId(), player.getName().getString(), e.getMessage());
                    }
                }
                entry.setOptions(visibleOptions.toArray(new DialogOption[0]));
            }
            visibleEntries.add(entry); // 将可见的条目（及其处理过的选项）添加到列表
        }
        playerSpecificSequence.setEntries(visibleEntries.toArray(new DialogEntry[0]));
        return playerSpecificSequence;
    }

    /**
     * (客户端) 接收并缓存单个对话数据。
     */
    @OnlyIn(Dist.CLIENT)
    public void receiveDialogData(String dialogId, String dialogJson) {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;
        try {
            DialogSequence sequence = GSON.fromJson(dialogJson, DialogSequence.class);
            if (sequence != null && sequence.getId() != null) {
                dialogSequences.put(sequence.getId(), sequence);
                // 确保在主线程显示对话界面
                Minecraft.getInstance().execute(() -> showDialog(dialogId));
            } else {
                Dialog.LOGGER.warn("Failed to parse the dialog data received from the server or the ID is null: {}", dialogId);
                sendPlayerMessage(Component.translatable("dialog.manager.received_sequence_empty", dialogId));
            }
        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to parse dialog '{}' JSON received from server", dialogId);
            sendPlayerMessage(Component.translatable("dialog.manager.received_parse_failed", dialogId, e.getMessage()));
            e.printStackTrace();
        }
    }

    /**
     * 显示指定ID的对话序列。
     */
    @OnlyIn(Dist.CLIENT)
    public void showDialog(String dialogId) {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;
        stopAutoPlay(); // 每次对话启动时重置自动播放为关闭状态
        DialogSequence sequence = getDialogSequence(dialogId);
        if (sequence == null) {
            NetworkHandler.sendRequestDialogToServer(dialogId);
            sendPlayerMessage(Component.translatable("dialog.manager.requesting_from_server", dialogId));
            return;
        }
        putDialogQueue(new WaitingDialogSequence(sequence));
    }

    /**
     * (客户端) 接收从服务端发送过来的、已经为当前玩家过滤好选项的完整对话序列，并显示它。
     *
     * @param dialogId     对话的ID (主要用于日志和潜在的映射键)。
     * @param sequenceJson 包含完整对话序列（已过滤选项）的JSON字符串。
     */
    @OnlyIn(Dist.CLIENT)
    public void receiveAndShowPlayerSpecificDialog(String dialogId, String sequenceJson) {
        receiveAndShowPlayerSpecificDialogWithEntity(dialogId, sequenceJson, null);
    }

    /**
     * 接收并显示带实体信息的玩家特定对话序列
     */
    @OnlyIn(Dist.CLIENT)
    public void receiveAndShowPlayerSpecificDialogWithEntity(String dialogId, String sequenceJson, Integer speakerEntityId) {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;

        stopAutoPlay(); // Reset auto-play

        DialogSequence playerSequence;
        try {
            playerSequence = GSON.fromJson(sequenceJson, DialogSequence.class);
        } catch (JsonSyntaxException e) {
            Dialog.LOGGER.error("Failed to parse player-specific dialog sequence JSON for ID {}: {}", dialogId, e.getMessage());
            sendPlayerMessage(Component.translatable("dialog.manager.received_sequence_parse_failed", dialogId, e.getMessage()));
            return;
        }

        if (playerSequence == null || playerSequence.getId() == null) {
            Dialog.LOGGER.warn("Parsed player-specific dialog sequence is null or has no ID. Original ID: {}", dialogId);
            sendPlayerMessage(Component.translatable("dialog.manager.received_sequence_empty", dialogId));
            return;
        }

        if (!dialogId.equals(playerSequence.getId())) {
            Dialog.LOGGER.warn("Dialog ID mismatch! Expected (from packet): {}, ID in parsed sequence: {}. Using ID from sequence.", dialogId, playerSequence.getId());
        }

        // 插入队列
        putDialogQueue(new WaitingDialogSequence(playerSequence, speakerEntityId));
    }

    @OnlyIn(Dist.CLIENT)
    private void putDialogQueue(@NotNull WaitingDialogSequence waitingDialogSequence) {
        if (!waitingDialogSequences.offer(waitingDialogSequence)) {
            Dialog.LOGGER.warn("Failed to insert dialog sequence into queue. Sequence ID: {}", waitingDialogSequence.dialogSequence().getId());
            sendPlayerMessage(Component.translatable("dialog.manager.sequence_queue.full", waitingDialogSequence.dialogSequence().getId()));
        }
        if (pollingThread != null && pollingThread.isAlive()) {
            return;
        }
        // 启动对话轮询线程
        pollingThread = new Thread(() -> {
            while (!waitingDialogSequences.isEmpty()) {
                pollingDialogSequence();
                try {
                    // 能被用户感知到这延迟，只会是对话框排队了，那两个对话框之间有最多1秒延迟并不会有影响
                    TimeUnit.SECONDS.sleep(1);
                } catch (Throwable ignored) {
                }
            }
        }, Dialog.MODID + "PollingThread");
        pollingThread.start();
    }

    @OnlyIn(Dist.CLIENT)
    public void pollingDialogSequence() {
        if (Minecraft.getInstance().player == null || Minecraft.getInstance().player.isDeadOrDying()) {
            return;
        }
        if (currentSequence != null && currentEntry != null) {
            if (currentSequence.getType() == DialogSequence.DialogType.SCREEN && Minecraft.getInstance().screen == null) {
                // 如果当前对话序列不为空，但当前没有已打开的屏幕，则清理当前对话序列
                // 此种情况可能是因为玩家在对话框中异常关闭了屏幕，如死亡后自动打开了死亡屏
                currentSequence = null;
                currentEntry = null;
                stopCurrentAudio();
            } else {
                return;
            }
        }
        var next = waitingDialogSequences.poll();
        if (next == null) {
            return;
        }
        currentSequence = next.dialogSequence();
        var speakerEntityId = next.speakerEntityId();

        // 确保在主线程显示对话界面
        Minecraft.getInstance().execute(() -> {
            // 获取说话实体
            net.minecraft.world.entity.Entity speakerEntity = null;
            if (Minecraft.getInstance().level != null && speakerEntityId != null) {
                speakerEntity = Minecraft.getInstance().level.getEntity(speakerEntityId);
            }

            clearDialogHistory();
            currentEntry = currentSequence.getFirstEntry();

            if (currentEntry == null) {
                Dialog.LOGGER.error("No entries found in player-specific dialog sequence: {}", currentSequence.getId());
                sendPlayerMessage(Component.translatable("dialog.manager.no_entries", currentSequence.getId()));
                currentSequence = null;
                return;
            }

            addDialogToHistory(currentEntry);

            // 获取玩家名称
            String playerName = "";
            if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.getGameProfile() != null) {
                playerName = Minecraft.getInstance().player.getGameProfile().getName();
            }
            this.currentDialogPlayerName = playerName;

            // 显示对话
            if (currentSequence.getType() == DialogSequence.DialogType.OVERLAY) {
                // 覆层形式的对话
                DialogOverlay.getInstance().setDialogEntry(currentSequence, currentEntry);
            } else {
                // 屏幕形式的对话
                Minecraft.getInstance().setScreen(new DialogScreen(currentSequence, currentEntry, this.currentDialogPlayerName, speakerEntity));
            }
        });
    }

    /**
     * 获取快速跳过标记。
     */
    public static boolean isFastForwardingNext() {
        return isFastForwardingNext;
    }

    /**
     * 设置快速跳过标记。
     */
    public static void setFastForwardingNext(boolean fastForwardingNext) {
        isFastForwardingNext = fastForwardingNext;
    }

    /**
     * 获取自动播放状态。
     */
    public static boolean isAutoPlaying() {
        return isAutoPlaying;
    }

    /**
     * 设置自动播放状态。
     */
    public static void setAutoPlaying(boolean autoPlaying) {
        isAutoPlaying = autoPlaying;
    }

    /**
     * 停止自动播放。
     */
    public static void stopAutoPlay() {
        isAutoPlaying = false;
    }

    /**
     * 显示对话序列中的下一条对话。
     */
    @OnlyIn(Dist.CLIENT)
    public void showNextDialog() {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;
        if (currentSequence == null || currentEntry == null) {
            return;
        }
        // 检查当前对话条目是否设置了结束对话标记
        if (currentEntry.isEndDialog()) {
            // 强制结束对话，关闭对话界面
            Minecraft.getInstance().setScreen(null);
            currentSequence = null;
            currentEntry = null;
            return;
        }

        DialogEntry nextEntry = currentSequence.getNextEntry(currentEntry);
        if (nextEntry == null) {
            // 对话结束，关闭对话界面
            Minecraft.getInstance().setScreen(null);
            currentSequence = null;
            currentEntry = null;
            return;
        }

        currentEntry = nextEntry;
        addDialogToHistory(currentEntry); // 将后续条目加入历史记录

        // 在创建新的DialogScreen之前停止当前音频
        stopCurrentAudio();

        // 更新对话界面
        Minecraft.getInstance().setScreen(new DialogScreen(currentSequence, currentEntry, this.currentDialogPlayerName));
    }

    /**
     * 显示对话序列中的下一条对话。
     */
    @OnlyIn(Dist.CLIENT)
    public void showNextDialogOverlay() {
        if (currentSequence == null || currentEntry == null) {
            return;
        }
        // 检查当前对话条目是否设置了结束对话标记
        if (currentEntry.isEndDialog()) {
            currentSequence = null;
            currentEntry = null;
            return;
        }

        DialogEntry nextEntry = currentSequence.getNextEntry(currentEntry);
        if (nextEntry == null) {
            currentSequence = null;
            currentEntry = null;
            DialogOverlay.getInstance().close();
            return;
        }

        currentEntry = nextEntry;
        addDialogToHistory(currentEntry); // 将后续条目加入历史记录

        // 在创建新的DialogScreen之前停止当前音频
        stopCurrentAudio();

        // 更新对话界面
        DialogOverlay.getInstance().setDialogEntry(currentSequence, currentEntry);
    }

    /**
     * 根据选项跳转到指定的对话。
     */
    @OnlyIn(Dist.CLIENT)
    public void jumpToDialog(String targetId) {
        if (Minecraft.getInstance() == null || !Minecraft.getInstance().level.isClientSide) return;
        if (currentSequence == null) {
            return;
        }

        DialogEntry targetEntry = currentSequence.findEntryById(targetId);
        if (targetEntry == null) {
            Dialog.LOGGER.error("Target dialog entry not found: {}", targetId);
            sendPlayerMessage(Component.translatable("dialog.manager.target_not_found", targetId));
            return;
        }

        currentEntry = targetEntry;
        addDialogToHistory(currentEntry); // 将跳转的条目加入历史记录

        // 在创建新的DialogScreen之前停止当前音频
        stopCurrentAudio();

        Minecraft.getInstance().setScreen(new DialogScreen(currentSequence, currentEntry, this.currentDialogPlayerName));
    }

    /**
     * (服务端) 在服务器上代表玩家执行命令。
     */
    public void executeCommands(Player player, List<String> commands) {
        executeCommands(player, commands, null);
    }

    /**
     * 执行指令列表，支持指定实体作为执行者
     *
     * @param player         玩家（用于向后兼容）
     * @param commands       指令列表
     * @param executorEntity 执行指令的实体，null表示使用玩家自己
     */
    public void executeCommands(Player player, List<String> commands, net.minecraft.world.entity.Entity executorEntity) {
        if (commands != null && !commands.isEmpty()) {
            for (String command : commands) {
                if (command != null && !command.isEmpty()) {
                    if (executorEntity != null) {
                        NetworkHandler.sendExecuteCommandToServerWithEntity(command, executorEntity.getId());
                    } else {
                        NetworkHandler.sendExecuteCommandToServer(command);
                    }
                }
            }
        }
    }

    // 保留旧的 executeCommand 方法以实现向后兼容

    /**
     * 播放对话音频（全局管理）
     */
    @OnlyIn(Dist.CLIENT)
    public static void playDialogAudio(String audioPath) {
        try {
            // 停止当前播放的音频
            stopCurrentAudio();

            // 移除.ogg后缀（如果存在）
            String soundName = audioPath.replace(".ogg", "");

            // 构建音频资源位置 - 使用sounds.json中定义的音频事件名称
            ResourceLocation audioLocation = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, soundName);

            // 创建音频实例并播放
            currentAudioInstance = SimpleSoundInstance.forUI(
                    SoundEvent.createVariableRangeEvent(audioLocation),
                    1.0f, // volume
                    1.0f  // pitch
            );

            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            soundManager.play(currentAudioInstance);

            audioStartTime = System.currentTimeMillis();
            audioEndTime = 0; // 重置结束时间
            audioPlaying = true;

        } catch (Exception e) {
            Dialog.LOGGER.error("Failed to play dialog audio: {}", audioPath, e);
        }
    }

    /**
     * 停止当前播放的音频（全局管理）
     */
    @OnlyIn(Dist.CLIENT)
    public static void stopCurrentAudio() {
        if (currentAudioInstance != null && audioPlaying) {
            SoundManager soundManager = Minecraft.getInstance().getSoundManager();
            soundManager.stop(currentAudioInstance);
            currentAudioInstance = null;
            audioPlaying = false;
            audioEndTime = 0; // 重置结束时间
        }
    }

    /**
     * 检查音频是否正在播放（全局管理）
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean isAudioPlaying() {
        return audioPlaying;
    }

    /**
     * 检查音频是否已完成播放（全局管理）
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean isAudioFinished() {
        if (!audioPlaying || currentAudioInstance == null) {
            return true;
        }

        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (!soundManager.isActive(currentAudioInstance)) {
            audioPlaying = false;
            audioEndTime = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    @Deprecated
    public void executeCommand(Player player, String command) {
        if (command != null && !command.isEmpty()) {
            List<String> singleCommandList = new ArrayList<>();
            singleCommandList.add(command);
            executeCommands(player, singleCommandList);
        }
    }

    public static void renderBackgroundImage(GuiGraphics guiGraphics, BackgroundImageDisplayData bgData, boolean isClosing, long fadeOutStartTime, int screenWidth, int screenHeight) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, bgData.getImageLocation());

        // 计算基于动画类型的透明度
        float alpha = 1.0F;

        // 优先处理关闭时的淡出效果
        if (isClosing && fadeOutStartTime > 0) {
            // 淡出阶段：从1到0
            long elapsedTime = System.currentTimeMillis() - fadeOutStartTime;
            if (elapsedTime < BACKGROUND_FADE_DURATION_MS) {
                alpha = Math.max(0.0F, 1.0F - (float) elapsedTime / BACKGROUND_FADE_DURATION_MS);
            } else {
                alpha = 0.0F;
            }
        } else {
            // 根据动画类型计算透明度
            switch (bgData.getAnimationType()) {
                case FADE_IN:
                    if (bgData.getAnimationStartTime() > 0) {
                        long elapsedTime = System.currentTimeMillis() - bgData.getAnimationStartTime();
                        if (elapsedTime < BACKGROUND_FADE_DURATION_MS) {
                            alpha = Math.min(1.0F, (float) elapsedTime / BACKGROUND_FADE_DURATION_MS);
                        }
                    }
                    break;
                case NONE:
                default:
                    break;
            }
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        int imgWidth = bgData.getImageWidth();
        int imgHeight = bgData.getImageHeight();

        BackgroundRenderOption renderOption = bgData.getRenderOption() != null ? bgData.getRenderOption() : BackgroundRenderOption.FILL;

        switch (renderOption) {
            case FILL:
                float screenAspect = (float) screenWidth / screenHeight;
                float imageAspect = (float) imgWidth / imgHeight;
                int drawWidth, drawHeight, drawX, drawY;
                if (imageAspect > screenAspect) {
                    drawHeight = screenHeight;
                    drawWidth = (int) (screenHeight * imageAspect);
                    drawX = (screenWidth - drawWidth) / 2;
                    drawY = 0;
                } else {
                    drawWidth = screenWidth;
                    drawHeight = (int) (screenWidth / imageAspect);
                    drawX = 0;
                    drawY = (screenHeight - drawHeight) / 2;
                }
                guiGraphics.blit(bgData.getImageLocation(), drawX, drawY, drawWidth, drawHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
            case FIT:
                screenAspect = (float) screenWidth / screenHeight;
                imageAspect = (float) imgWidth / imgHeight;
                if (imageAspect > screenAspect) {
                    drawWidth = screenWidth;
                    drawHeight = (int) (screenWidth / imageAspect);
                } else {
                    drawHeight = screenHeight;
                    drawWidth = (int) (screenHeight * imageAspect);
                }
                drawX = (screenWidth - drawWidth) / 2;
                drawY = (screenHeight - drawHeight) / 2;
                guiGraphics.blit(bgData.getImageLocation(), drawX, drawY, drawWidth, drawHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
            case STRETCH:
                guiGraphics.blit(bgData.getImageLocation(), 0, 0, screenWidth, screenHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
            case TILE:
                for (int y = 0; y < screenHeight; y += imgHeight) {
                    for (int x = 0; x < screenWidth; x += imgWidth) {
                        int w = Math.min(imgWidth, screenWidth - x);
                        int h = Math.min(imgHeight, screenHeight - y);
                        guiGraphics.blit(bgData.getImageLocation(), x, y, 0, 0, w, h, imgWidth, imgHeight);
                    }
                }
                break;
            case CENTER:
                drawX = (screenWidth - imgWidth) / 2;
                drawY = (screenHeight - imgHeight) / 2;
                guiGraphics.blit(bgData.getImageLocation(), drawX, drawY, imgWidth, imgHeight, 0, 0, imgWidth, imgHeight, imgWidth, imgHeight);
                break;
        }
        RenderSystem.disableBlend();
    }

    public static void renderPortrait(GuiGraphics guiGraphics, List<PortraitDisplayData> portraitDisplayList, int screenWidth, int screenHeight, float portraitHeightPercentage, int offsetX, int offsetY) {
        for (PortraitDisplayData displayData : portraitDisplayList) {
            if (displayData.isLoadedSuccessfully() && displayData.getResourceLocation() != null && displayData.getActualWidth() > 0 && displayData.getActualHeight() > 0) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);

                RenderSystem.setShaderTexture(0, displayData.getResourceLocation());
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();

                int portraitRenderHeight = (int) (screenHeight * portraitHeightPercentage); // 固定高度
                float aspectRatio = (float) displayData.getActualWidth() / displayData.getActualHeight();
                int portraitRenderWidth = (int) (portraitRenderHeight * aspectRatio); // 等比例计算宽度

                int baseX = 0, baseY = 0;
                float currentScale = displayData.getSize(); // 使用size属性控制缩放
                float currentAlpha = 1.0f;
                float yOffset = 0;
                float xOffset = 0;

                long currentTime = System.currentTimeMillis();
                float progress = 1.0f;

                if (ClientConfig.ENABLE_PORTRAIT_ANIMATIONS.get() && displayData.getAnimationType() != PortraitAnimationType.NONE && displayData.getAnimationStartTime() != -1) {
                    long elapsedTime = currentTime - displayData.getAnimationStartTime();
                    if (elapsedTime < ANIMATION_DURATION_MS) {
                        progress = (float) elapsedTime / ANIMATION_DURATION_MS;
                    } else {
                        displayData.setAnimationStartTime(-1);
                    }

                    switch (displayData.getAnimationType()) {
                        case FADE_IN:
                            currentAlpha = Mth.lerp(progress, 0f, 1f);
                            break;
                        case SLIDE_IN_FROM_BOTTOM:
                            yOffset = Mth.lerp(progress, 50f, 0f);
                            break;
                        case BOUNCE:
                            if (progress < 0.5f) {
                                yOffset = Mth.lerp(progress * 2, 0f, -20f);
                            } else {
                                yOffset = Mth.lerp((progress - 0.5f) * 2, -20f, 0f);
                            }
                            break;
                        case NONE:
                        default:
                            break;
                    }
                }
                if (displayData.getBrightness() == 0.0f) {
                    RenderSystem.setShaderColor(0.0f, 0.0f, 0.0f, 1.0f); // 纯黑剪影，完全不透明
                } else {
                    //使用brightness调整RGB，currentAlpha处理透明度
                    RenderSystem.setShaderColor(displayData.getBrightness(), displayData.getBrightness(), displayData.getBrightness(), currentAlpha);
                }

                int scaledWidth = (int) (portraitRenderWidth * currentScale);
                int scaledHeight = (int) (portraitRenderHeight * currentScale);

                baseY = switch (displayData.getPosition()) {
                    case LEFT -> {
                        baseX = offsetX - scaledWidth >= 0 ? (offsetX - scaledWidth) : 20;
                        yield screenHeight - scaledHeight;
                    }
                    case RIGHT -> {
                        baseX = offsetX > scaledWidth ? (screenWidth - offsetX) : (screenWidth - scaledWidth - 20);
                        yield screenHeight - scaledHeight;
                    }
                    default -> {
                        baseX = (screenWidth - scaledWidth) / 2;
                        yield screenHeight - scaledHeight;
                    }
                };

                int finalX = baseX + (int) xOffset;
                int finalY = baseY + (int) yOffset - offsetY;

                guiGraphics.blit(displayData.getResourceLocation(), finalX, finalY, 0, 0, scaledWidth, scaledHeight, scaledWidth, scaledHeight);
                RenderSystem.disableBlend();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // 重置颜色
            }
        }
    }

    public static void renderDialogBackground(GuiGraphics guiGraphics, String imagePath, int dialogBoxX, int dialogBoxY, int dialogBoxWidth, int dialogBoxHeight) {
        if (imagePath != null && !imagePath.isEmpty()) {
            try {
                ResourceLocation dialogBgRl = ResourceLocation.fromNamespaceAndPath(Dialog.MODID, imagePath);

                RenderSystem.setShader(GameRenderer::getPositionTexShader); // 确保使用正确的着色器
                RenderSystem.setShaderTexture(0, dialogBgRl); // 绑定纹理
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F); // 重置颜色，确保图片不受先前渲染影响
                RenderSystem.enableBlend(); // 为透明图片启用混合
                RenderSystem.defaultBlendFunc(); // 使用默认混合函数

                // 将图片拉伸至对话框大小进行渲染
                guiGraphics.blit(dialogBgRl, dialogBoxX, dialogBoxY, 0, 0.0F, 0.0F, dialogBoxWidth, dialogBoxHeight, dialogBoxWidth, dialogBoxHeight);

                RenderSystem.disableBlend(); // 绘制完毕后禁用混合
            } catch (Exception e) {
                Dialog.LOGGER.error("Failed to render dialog background image: {}. Falling back to solid color.", imagePath, e);
                // 回退到纯色背景
                int backgroundColor = ClientConfig.DIALOG_BACKGROUND_COLOR.get();
                int opacity = ClientConfig.DIALOG_BACKGROUND_OPACITY.get();
                int color = (opacity << 24) | (backgroundColor & 0xFFFFFF);
                guiGraphics.fill(dialogBoxX, dialogBoxY, dialogBoxX + dialogBoxWidth, dialogBoxY + dialogBoxHeight, color);
            }
        } else {
            // 默认纯色背景
            int backgroundColor = ClientConfig.DIALOG_BACKGROUND_COLOR.get();
            int opacity = ClientConfig.DIALOG_BACKGROUND_OPACITY.get();
            int color = (opacity << 24) | (backgroundColor & 0xFFFFFF);
            guiGraphics.fill(dialogBoxX, dialogBoxY, dialogBoxX + dialogBoxWidth, dialogBoxY + dialogBoxHeight, color);
        }
    }

    public static Component subText(Component text, int length) {
        MutableComponent sub = Component.empty();
        int subLength = 0;
        for (Component sibling : text.getSiblings()) {
            int maxLength = length - subLength;
            String siblingText = sibling.getString(maxLength);
            if (!siblingText.isEmpty()) {
                subLength += siblingText.length();
                sub.append(Component.literal(siblingText).withStyle(sibling.getStyle()));
            } else {
                break;
            }
        }
        return sub;
    }
}
