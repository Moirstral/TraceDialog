package top.yourzi.dialog.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;
import top.yourzi.dialog.DialogManager;

/**
 * 服务端向客户端发送特定对话数据的网络包。
 */
public record SendDialogDataPacket(String dialogId, String dialogJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SendDialogDataPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "send_dialog_data_packet"));

    public static final StreamCodec<FriendlyByteBuf, SendDialogDataPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            SendDialogDataPacket::dialogId,
            ByteBufCodecs.STRING_UTF8,
            SendDialogDataPacket::dialogJson,
            SendDialogDataPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final SendDialogDataPacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 确保在客户端线程中执行
            handleOnClient(message);
        });
    }

    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(final SendDialogDataPacket message) {
        DialogManager.getInstance().receiveDialogData(message.dialogId(), message.dialogJson());
    }
}