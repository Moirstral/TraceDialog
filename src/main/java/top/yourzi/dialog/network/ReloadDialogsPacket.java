package top.yourzi.dialog.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

/**
 * 重新加载对话的网络包
 */
public record ReloadDialogsPacket() implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ReloadDialogsPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "reload_dialogs_packet"));

    public static final StreamCodec<ByteBuf, ReloadDialogsPacket> STREAM_CODEC = StreamCodec.composite(
            null, null, null
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理接收到的包
     */
    public static void handle(final ReloadDialogsPacket message, final IPayloadContext context) {
        // 确保在客户端线程中执行
        context.enqueueWork(ReloadDialogsPacket::handleOnClient);
    }

    /**
     * 在客户端处理包
     */
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient() {
        // 在客户端重新加载对话
        Minecraft.getInstance().execute(() -> {
            DialogManager.getInstance().loadDialogsFromServer(Minecraft.getInstance().getResourceManager());
        });
    }
}