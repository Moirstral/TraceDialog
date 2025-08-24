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

import java.util.HashMap;
import java.util.Map;

/**
 * 网络数据包，用于从服务端向客户端同步所有对话数据。
 */
public record SyncAllDialogsPacket(Map<String, String> dialogDataMap) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncAllDialogsPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "sync_all_dialogs_packet"));

    public static final StreamCodec<FriendlyByteBuf, SyncAllDialogsPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8),
            SyncAllDialogsPacket::dialogDataMap,
            SyncAllDialogsPacket::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理接收到的包 (在客户端执行)。
     */
    public static void handle(final SyncAllDialogsPacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            // 确保在客户端线程中执行
            handleOnClient(message);
        });
    }

    /**
     * 在客户端处理包的具体逻辑。
     */
    @OnlyIn(Dist.CLIENT)
    private static void handleOnClient(final SyncAllDialogsPacket message) {
        DialogManager.getInstance().receiveAllDialogsFromServer(message.dialogDataMap());
    }
}