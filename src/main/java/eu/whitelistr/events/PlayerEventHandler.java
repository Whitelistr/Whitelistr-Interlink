package eu.whitelistr.events;

import eu.whitelistr.network.WClient;
import eu.whitelistr.data.PlayerInfo;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.net.InetSocketAddress;
import com.google.gson.JsonObject;

public class PlayerEventHandler implements Listener {

    private final WClient webSocketClient;

    public PlayerEventHandler(WClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        InetSocketAddress remoteAddress = (InetSocketAddress) event.getPlayer().getAddress();
        if (remoteAddress != null) {
            String playerIP = remoteAddress.getAddress().getHostAddress();

            PlayerInfo playerInfo = new PlayerInfo(
                    playerIP,
                    event.getPlayer().getDisplayName(),
                    event.getPlayer().getUniqueId().toString(),
                    remoteAddress.getHostName(),
                    System.currentTimeMillis(),
                    ConfigHandler.SERVER_UUID
            );
            sendPlayerDataToWebServer(playerInfo);
            if (!webSocketClient.isPlayerWhitelisted(playerInfo.getUsername())) {
                event.getPlayer().kickPlayer("You are not whitelisted!");
            } else {
                System.out.println("Player " + playerInfo.getUsername() + " joined successfully.");
            }
        } else {
            System.err.println("Unable to get remote address for player " + event.getPlayer().getDisplayName());
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