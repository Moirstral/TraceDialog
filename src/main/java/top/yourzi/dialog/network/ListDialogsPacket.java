package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;

import java.util.ArrayList;
import java.util.List;

/**
 * 列出所有对话的网络包
 */
public record ListDialogsPacket(List<String> dialogIds, List<String> dialogNames) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ListDialogsPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "list_dialogs_packet"));

    public static final StreamCodec<FriendlyByteBuf, ListDialogsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8),
            ListDialogsPacket::dialogIds,
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8),
            ListDialogsPacket::dialogNames,
            ListDialogsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理接收到的包
     */
    public static void handle(final ListDialogsPacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 确保在客户端线程中执行
            handleOnClient(message);
        });
    }

    /**
     * 在客户端处理包
     */
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(final ListDialogsPacket message) {
        // 在客户端显示对话列表
        Minecraft.getInstance().execute(() -> {
            for (int i = 0; i < message.dialogIds().size(); i++) {
                if (Minecraft.getInstance().player != null) {
                    Minecraft.getInstance().player.sendSystemMessage(
                            Component.literal(
                                    "   - " + message.dialogIds().get(i) + " (" + message.dialogNames().get(i) + ")"
                            )
                    );
                }
            }
        });
    }
}