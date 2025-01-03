package eu.whitelistr.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import eu.whitelistr.network.WClient;
import eu.whitelistr.data.PlayerInfo;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;

import java.net.InetSocketAddress;
import com.google.gson.JsonObject;

public class PlayerEventHandler {

    private final WClient webSocketClient;

    public PlayerEventHandler(WClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            NetworkManager networkManager = ((EntityPlayerMP) event.player).playerNetServerHandler.netManager;
            InetSocketAddress remoteAddress = (InetSocketAddress) networkManager.getSocketAddress();
            String playerIP = remoteAddress.getAddress().getHostAddress();

            PlayerInfo playerInfo = new PlayerInfo(
                playerIP,
                event.player.getDisplayName(),
                event.player.getUniqueID().toString(),
                remoteAddress.getHostName(),
                System.currentTimeMillis(),
                ConfigHandler.SERVER_UUID
            );
            sendPlayerDataToWebServer(playerInfo);
            if (!webSocketClient.isPlayerWhitelisted(playerInfo.getUsername())) {
                ((EntityPlayerMP) event.player).playerNetServerHandler.kickPlayerFromServer("You are not whitelisted!");
            } else {
                System.out.println("Player " + playerInfo.getUsername() + " joined successfully.");
            }
        }
    }

    private void sendPlayerDataToWebServer(PlayerInfo playerInfo) {
        JsonObject json = new JsonObject();
        json.addProperty("action", "onJoinPlayer");
        json.addProperty("uuid", playerInfo.getUuid());
        json.addProperty("username", playerInfo.getUsername());
        json.addProperty("serverUUID", playerInfo.getServerId());
        json.addProperty("joinDate", playerInfo.getTimestamp());

        JsonObject metadata = new JsonObject();
        metadata.addProperty("ip", playerInfo.getIp());
        metadata.addProperty("hostname", playerInfo.getConnectionAddress());
        json.add("metadata", metadata);

        if (webSocketClient.isOpen()) {
            webSocketClient.send(json.toString());
        } else {
            System.err.println("WebSocket is not connected. Unable to send player data.");
        }
    }
}


