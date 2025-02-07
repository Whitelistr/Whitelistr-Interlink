package eu.whitelistr.events;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.network.WClient;
import eu.whitelistr.data.PlayerInfo;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;

import java.net.InetSocketAddress;
import com.google.gson.JsonObject;

public class PlayerEventHandler {

    private final WClient webSocketClient;
    private final Cache whitelistCache;

    public PlayerEventHandler(WClient webSocketClient, Cache whitelistCache) {
        if (webSocketClient == null || whitelistCache == null) {
            throw new IllegalArgumentException("WebSocketClient and WhitelistCache cannot be null");
        }
        this.webSocketClient = webSocketClient;
        this.whitelistCache = whitelistCache;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            NetworkManager networkManager = ((EntityPlayerMP) event.player).playerNetServerHandler.netManager;
            InetSocketAddress remoteAddress = (InetSocketAddress) networkManager.getSocketAddress();
            String playerIP = remoteAddress.getAddress().getHostAddress();
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            String uuid = player.getUniqueID().toString();

            PlayerInfo playerInfo = new PlayerInfo(
                playerIP,
                event.player.getDisplayName(),
                event.player.getUniqueID().toString(),
                remoteAddress.getHostName(),
                System.currentTimeMillis(),
                ConfigHandler.SERVER_UUID
            );
            sendPlayerDataToWebServer(playerInfo);
            if (!whitelistCache.isPlayerWhitelisted(uuid)) {
                player.playerNetServerHandler.kickPlayerFromServer("You are not whitelisted!");
            } else {
                FMLLog.info("Player " + player.getDisplayName() + " joined successfully.");
            }
        }
    }

    private void sendPlayerDataToWebServer(PlayerInfo playerInfo) {
        if (playerInfo == null || webSocketClient == null) {
            FMLLog.bigWarning("[Whitelistr] Error: PlayerInfo or WebSocketClient is null!");
            return;
        }

        JsonObject json = new JsonObject();
        json.addProperty("action", "onJoinPlayer");
        json.addProperty("uuid", playerInfo.getUuid() != null ? playerInfo.getUuid() : "Unknown");
        json.addProperty("username", playerInfo.getUsername() != null ? playerInfo.getUsername() : "Unknown");
        json.addProperty("serverUUID", playerInfo.getServerId() != null ? playerInfo.getServerId() : "Unknown");
        json.addProperty("joinDate", playerInfo.getTimestamp());

        JsonObject metadata = new JsonObject();
        metadata.addProperty("ip", playerInfo.getIp() != null ? playerInfo.getIp() : "Unknown");
        metadata.addProperty("hostname", playerInfo.getConnectionAddress() != null ? playerInfo.getConnectionAddress() : "Unknown");
        json.add("metadata", metadata);

        if (webSocketClient.isOpen()) {
            webSocketClient.send(json.toString());
        } else {
            FMLLog.warning("WebSocket is not open, cannot send player data.");
        }
    }


}


