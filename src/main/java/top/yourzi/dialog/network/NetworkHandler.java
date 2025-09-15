package top.yourzi.dialog.network;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import top.yourzi.dialog.Dialog;

import java.util.List;

/**
 * 网络包处理器，用于服务端和客户端之间的通信
 */
@SuppressWarnings("removal")
public class NetworkHandler {

    /**
     * 初始化网络包处理器
     */
    public static void init(final RegisterPayloadHandlersEvent event) {
        // 设置当前网络版本
        final PayloadRegistrar registrar = event.registrar("1").executesOn(HandlerThread.NETWORK);

        // 注册从服务器到客户端的对话显示包
        registrar.playToClient(
                ShowDialogPacket.TYPE,
                ShowDialogPacket.STREAM_CODEC,
                ShowDialogPacket::handle
        );

        // 注册从服务器到客户端的重新加载对话包
        registrar.playToClient(
                ReloadDialogsPacket.TYPE,
                ReloadDialogsPacket.STREAM_CODEC,
                ReloadDialogsPacket::handle
        );

        // 注册从服务器到客户端的对话列表包
        registrar.playToClient(
                ListDialogsPacket.TYPE,
                ListDialogsPacket.STREAM_CODEC,
                ListDialogsPacket::handle
        );

        // 注册从客户端到服务端的请求对话包
        registrar.playToServer(
                RequestDialogPacket.TYPE,
                RequestDialogPacket.STREAM_CODEC,
                RequestDialogPacket::handle
        );

        // 注册从服务端到客户端的发送对话数据包
        registrar.playToClient(
                SendDialogDataPacket.TYPE,
                SendDialogDataPacket.STREAM_CODEC,
                SendDialogDataPacket::handle
        );

        // 注册从服务端到客户端的同步所有对话数据包
        registrar.playToClient(
                SyncAllDialogsPacket.TYPE,
                SyncAllDialogsPacket.STREAM_CODEC,
                SyncAllDialogsPacket::handle
        );

        // 注册从客户端到服务端的执行命令包
        registrar.playToServer(
                ExecuteServerCommandPacket.TYPE,
                ExecuteServerCommandPacket.STREAM_CODEC,
                ExecuteServerCommandPacket::handle
        );

        // 注册从服务端到客户端的带实体信息的对话显示包
        registrar.playToClient(
                ShowDialogWithEntityPacket.TYPE,
                ShowDialogWithEntityPacket.STREAM_CODEC,
                ShowDialogWithEntityPacket::handle
        );

        // 注册从服务端到客户端的带实体信息的对话显示包
        registrar.playToServer(
                PlayerInvinciblePacket.TYPE,
                PlayerInvinciblePacket.STREAM_CODEC,
                PlayerInvinciblePacket::handle
        );
    }

    /**
     * 向指定玩家发送显示对话的网络包
     */
    public static void sendShowDialogToPlayer(ServerPlayer player, String dialogId, String dialogJson) {
        PacketDistributor.sendToPlayer(player, new ShowDialogPacket(dialogId, dialogJson));
    }

    /**
     * 向指定玩家发送带实体信息的显示对话的网络包
     */
    public static void sendShowDialogToPlayerWithEntity(ServerPlayer player, String dialogId, String dialogJson, net.minecraft.world.entity.Entity speakerEntity) {
        PacketDistributor.sendToPlayer(player, new ShowDialogWithEntityPacket(dialogId, dialogJson, speakerEntity.getId()));
    }

    /**
     * 向指定玩家发送重新加载对话的网络包
     */
    public static void sendReloadDialogsToPlayer(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new ReloadDialogsPacket());
    }

    /**
     * 向所有玩家发送重新加载对话的网络包
     */
    public static void sendReloadDialogsToAll() {
        PacketDistributor.sendToAllPlayers(new ReloadDialogsPacket());
    }

    /**
     * 向指定玩家发送对话列表的网络包
     */
    public static void sendDialogListToPlayer(ServerPlayer player, List<String> dialogIds, List<String> dialogNames) {
        PacketDistributor.sendToPlayer(player, new ListDialogsPacket(dialogIds, dialogNames));
    }

    /**
     * 客户端向服务端发送请求特定对话数据的网络包
     */
    public static void sendRequestDialogToServer(String dialogId) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new RequestDialogPacket(dialogId));
        } else {
            Dialog.LOGGER.warn("Cannot send RequestDialogPacket: not on client or no connection.");
        }
    }

    /**
     * 服务端向指定玩家发送特定对话数据的网络包
     */
    public static void sendDialogDataToPlayer(ServerPlayer player, String dialogId, String dialogJson) {
        PacketDistributor.sendToPlayer(player, new SendDialogDataPacket(dialogId, dialogJson));
    }

    /**
     * 服务端向指定玩家发送所有对话数据的网络包。
     */
    public static void sendAllDialogsToPlayer(ServerPlayer player, java.util.Map<String, String> dialogDataMap) {
        PacketDistributor.sendToAllPlayers(new SyncAllDialogsPacket(dialogDataMap));
    }

    /**
     * 服务端向所有玩家发送所有对话数据的网络包。
     */
    public static void sendAllDialogsToAllPlayers(java.util.Map<String, String> dialogDataMap) {
        PacketDistributor.sendToAllPlayers(new SyncAllDialogsPacket(dialogDataMap));
    }

    /**
     * 客户端向服务端发送执行命令请求的网络包
     */
    public static void sendExecuteCommandToServer(String command) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ExecuteServerCommandPacket(command));
        } else {
            Dialog.LOGGER.warn("Cannot send ExecuteServerCommandPacket: not on client or no connection.");
        }
    }

    /**
     * 客户端向服务端发送带实体信息的执行命令请求
     */
    public static void sendExecuteCommandToServerWithEntity(String command, int executorEntityId) {
        if (Minecraft.getInstance() != null && Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new ExecuteServerCommandPacket(command, executorEntityId));
        } else {
            Dialog.LOGGER.warn("Cannot send ExecuteServerCommandPacket with entity: not on client or no connection.");
        }
    }

    /**
     * 玩家向服务端发送设置玩家无敌的网络包
     */
    public static void sendPlayerInvinciblePacketToServer(int status) {
        if (status < 0 || status > 2) {
            Dialog.LOGGER.warn("Invalid status for PlayerInvinciblePacket: {}", status);
        }
        if (Minecraft.getInstance().getConnection() != null) {
            PacketDistributor.sendToServer(new PlayerInvinciblePacket((byte) status));
        } else {
            Dialog.LOGGER.warn("Cannot send PlayerInvinciblePacket: not on client or no connection.");
        }
    }
}