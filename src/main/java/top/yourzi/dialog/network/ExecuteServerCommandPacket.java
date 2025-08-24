package top.yourzi.dialog.network;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import top.yourzi.dialog.Dialog;

/**
 * 用于从客户端向服务器发送命令执行请求的网络包。
 */
public record ExecuteServerCommandPacket(
        String command,
        int executorEntityId  // 执行指令的实体ID，-1表示使用玩家自己
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ExecuteServerCommandPacket> TYPE = new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Dialog.MODID, "execute_server_command_packet"));

    public static final StreamCodec<FriendlyByteBuf, ExecuteServerCommandPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ExecuteServerCommandPacket::command,
            ByteBufCodecs.INT,
            ExecuteServerCommandPacket::executorEntityId,
            ExecuteServerCommandPacket::new
    );

    public ExecuteServerCommandPacket(String command) {
        this(command, -1);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * 处理接收到的包（在服务端）。
     * 方法名和签名需要匹配 NetworkHandler 中注册的处理器。
     */
    public static void handle(final ExecuteServerCommandPacket message, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();// 获取发送此数据包的玩家
            if (sender == null) {
                Dialog.LOGGER.warn("ExecuteServerCommandPacket received from null sender.");
                return;
            }

            MinecraftServer server = sender.getServer();
            if (server == null) {
                Dialog.LOGGER.warn("ExecuteServerCommandPacket handler: MinecraftServer instance is null.");
                return;
            }


            CommandSourceStack commandSource;

            if (message.executorEntityId() == -1) {
                // 使用玩家自己作为指令执行者（向后兼容）
                commandSource = sender.createCommandSourceStack()
                        .withPermission(Commands.LEVEL_GAMEMASTERS)
                        .withSuppressedOutput();
            } else {
                // 使用指定实体作为指令执行者
                net.minecraft.world.entity.Entity executorEntity = sender.level().getEntity(message.executorEntityId());
                if (executorEntity != null) {
                    commandSource = new CommandSourceStack(
                            executorEntity,
                            executorEntity.position(),
                            executorEntity.getRotationVector(),
                            sender.serverLevel(),
                            Commands.LEVEL_GAMEMASTERS,
                            executorEntity.getName().getString(),
                            executorEntity.getDisplayName(),
                            server,
                            executorEntity
                    ).withSuppressedOutput();
                } else {
                    Dialog.LOGGER.warn("ExecuteServerCommandPacket: Executor entity with ID {} not found, falling back to player.", message.executorEntityId());
                    commandSource = sender.createCommandSourceStack()
                            .withPermission(Commands.LEVEL_GAMEMASTERS)
                            .withSuppressedOutput();
                }
            }

            try {
                server.getCommands().performPrefixedCommand(commandSource, message.command());
            } catch (Exception e) {
                Dialog.LOGGER.error("Error executing command on server: {}", message.command(), e);
            }
        });
    }
}