package top.yourzi.dialog.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import top.yourzi.dialog.Dialog;

/**
 * 对话系统的客户端配置类。
 */
@EventBusSubscriber(modid = Dialog.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder()
            .comment("对话系统客户端配置")
            .push("dialog_client");

    // 对话框UI配置
    public static ModConfigSpec.IntValue DIALOG_BOX_WIDTH; // 对话框宽度
    public static ModConfigSpec.IntValue DIALOG_BOX_HEIGHT; // 对话框高度
    public static ModConfigSpec.IntValue DIALOG_BOX_PADDING; // 对话框内边距
    public static ModConfigSpec.ConfigValue<Integer> DIALOG_TEXT_COLOR; // 对话文本默认颜色
    public static ModConfigSpec.ConfigValue<Integer> DIALOG_BACKGROUND_COLOR; // 对话框背景颜色
    public static ModConfigSpec.IntValue DIALOG_BACKGROUND_OPACITY; // 对话框背景不透明度
    public static ModConfigSpec.BooleanValue USE_CUSTOM_BUTTON_TEXTURE; // 启用自定义按钮

    // 立绘配置
    public static ModConfigSpec.BooleanValue ENABLE_PORTRAIT_ANIMATIONS; // 启用立绘动画

    // 对话系统配置
    public static ModConfigSpec.BooleanValue IS_PAUSE_SCREEN; // 是否在对话时暂停游戏（仅单人）
    public static ModConfigSpec.IntValue AUTO_ADVANCE_DELAY; // 自动推进对话延迟 (毫秒)
    public static ModConfigSpec.BooleanValue SHOW_SPEAKER_NAME; // 显示说话者名称
    public static ModConfigSpec.IntValue TEXT_ANIMATION_SPEED; // 文本逐字显示速度 (每秒字符数，0表示立即显示全部)

    static {
        BUILDER.comment("对话框UI配置").push("ui");
        DIALOG_BOX_WIDTH = BUILDER.comment("对话框宽度").defineInRange("dialogBoxWidth", 320, 0, Integer.MAX_VALUE);
        DIALOG_BOX_HEIGHT = BUILDER
                .comment("对话框高度")
                .defineInRange("dialogBoxHeight", 100, 0, Integer.MAX_VALUE);
        DIALOG_BOX_PADDING = BUILDER
                .comment("对话框内边距")
                .defineInRange("dialogBoxPadding", 10, 0, Integer.MAX_VALUE);
        DIALOG_TEXT_COLOR = BUILDER
                .comment("对话文本默认颜色 (ARGB格式)")
                .define("dialogTextColor", 0xFFFFFFFF);
        DIALOG_BACKGROUND_COLOR = BUILDER
                .comment("对话框背景默认颜色 (RGB格式)")
                .define("dialogBackgroundColor", 0x000000);
        DIALOG_BACKGROUND_OPACITY = BUILDER
                .comment("对话框背景不透明度 (0-255)")
                .defineInRange("dialogBackgroundOpacity", 200, 0, Integer.MAX_VALUE);
        USE_CUSTOM_BUTTON_TEXTURE = BUILDER
                .comment("是否使用自定义按钮纹理（否则使用Minecraft原版按钮纹理）")
                .define("useCustomButtonTexture", false);
        BUILDER.pop();

        BUILDER.comment("立绘配置").push("portrait");
        ENABLE_PORTRAIT_ANIMATIONS = BUILDER
                .comment("启用立绘动画")
                .define("enablePortraitAnimations", true);
        BUILDER.pop();

        BUILDER.comment("对话系统配置").push("system");
        IS_PAUSE_SCREEN = BUILDER
                .comment("是否在对话时暂停游戏（仅单人模式）")
                .define("isPauseScreen", false);
        AUTO_ADVANCE_DELAY = BUILDER
                .comment("自动推进对话的延迟时间（毫秒）")
                .defineInRange("autoAdvanceDelay", 700, 0, Integer.MAX_VALUE);
        SHOW_SPEAKER_NAME = BUILDER
                .comment("是否显示说话者的名称")
                .define("showSpeakerName", true);
        TEXT_ANIMATION_SPEED = BUILDER
                .comment("文本逐字显示的速度（每秒字符数，设置为0则立即显示全部文本）")
                .defineInRange("textAnimationSpeed", 20, 0, 1000);
        BUILDER.pop();
    }

    // 构建配置
    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}