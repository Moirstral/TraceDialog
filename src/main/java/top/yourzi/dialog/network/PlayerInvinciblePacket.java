package top.yourzi.dialog.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;
import top.yourzi.dialog.Dialog;

/**
 * 客户端玩家发送消息标识玩家是否无敌
 *
 * @param status 0: 不无敌 1: 无敌 2: 无敌且隐身
 */
public record PlayerInvinciblePacket(Byte status) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PlayerInvinciblePacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "player_invincible_packet"));

    public static final StreamCodec<ByteBuf, PlayerInvinciblePacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE,
            PlayerInvinciblePacket::status,
            PlayerInvinciblePacket::new
    );

    @Override
    public @NotNull Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理接收到的包（在服务端）。
     * 方法名和签名需要匹配 NetworkHandler 中注册的处理器。
     */
    public static void handle(final PlayerInvinciblePacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.gameMode.isSurvival()) {
                // 仅生存模式有效
                return;
            }
            MinecraftServer server = player.getServer();
            if (server == null) {
                Dialog.LOGGER.warn("ExecuteServerCommandPacket handler: MinecraftServer instance is null.");
                return;
            }

            // TODO 这样无敌可能会跟其他 MOD 冲突，可能会因为对话框结束后影响了其他 MOD 的无敌
            player.setInvisible(message.status() == 2);
            player.setNoGravity(message.status() == 2);
            player.setInvulnerable(message.status() != 0);
        });
    }
}
