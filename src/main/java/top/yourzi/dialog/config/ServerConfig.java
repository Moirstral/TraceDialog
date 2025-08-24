package top.yourzi.dialog.config;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import top.yourzi.dialog.Dialog;

/**
 * 对话系统的服务端配置类。
 */
@EventBusSubscriber(modid = Dialog.MODID, bus = EventBusSubscriber.Bus.MOD)
public class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder()
            .comment("对话系统服务端配置")
            .push("dialog_server");

    // 对话系统服务端配置
    public static ModConfigSpec.BooleanValue ALLOW_SKIP_DIALOG; // 是否允许跳过对话

    static {
        BUILDER.comment("对话控制配置").push("control");
        ALLOW_SKIP_DIALOG = BUILDER
                .comment("是否允许玩家使用Ctrl键跳过对话")
                .define("allowSkipDialog", true);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
    }
}