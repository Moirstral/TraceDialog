package top.yourzi.dialog.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
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
 * 服务端向客户端发送带实体信息的对话显示包。
 */
public record ShowDialogWithEntityPacket(String dialogId, String dialogJson,
                                         int speakerEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ShowDialogWithEntityPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "show_dialog_with_entity_packet"));

    public static final StreamCodec<ByteBuf, ShowDialogWithEntityPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ShowDialogWithEntityPacket::dialogId,
            ByteBufCodecs.STRING_UTF8,
            ShowDialogWithEntityPacket::dialogJson,
            ByteBufCodecs.INT,
            ShowDialogWithEntityPacket::speakerEntityId,
            ShowDialogWithEntityPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理接收到的包
     */
    public static void handle(final ShowDialogWithEntityPacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 确保在客户端线程中执行
            handleOnClient(message);
        });
    }

    /**
     * 在客户端处理包
     */
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(final ShowDialogWithEntityPacket message) {
        Minecraft.getInstance().execute(() -> {
            // 获取说话实体
//            Entity speakerEntity = null;
//            if (Minecraft.getInstance().level != null) {
//                speakerEntity = Minecraft.getInstance().level.getEntity(message.speakerEntityId());
//            }

            // 接收并显示带实体信息的对话
            DialogManager.getInstance().receiveAndShowPlayerSpecificDialogWithEntity(message.dialogId(), message.dialogJson(), message.speakerEntityId());
        });
    }
}