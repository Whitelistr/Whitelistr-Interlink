package eu.whitelistr.events;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.network.WClient;
import eu.whitelistr.data.PlayerInfo;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText; 

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
    public void onPlayerLogin(PlayerEvent.Login event) { // Changed to PlayerEvent.Login
        if (event.player instanceof EntityPlayerMP) {
            NetworkManager networkManager = ((EntityPlayerMP) event.player).playerNetServerHandler.netManager;
            InetSocketAddress remoteAddress = (InetSocketAddress) networkManager.getSocketAddress();
            String playerIP = remoteAddress.getAddress().getHostAddress();
            EntityPlayerMP player = (EntityPlayerMP) event.player;
            String uuid = player.getUniqueID().toString().replace("-", "");

            PlayerInfo playerInfo = new PlayerInfo(
                playerIP,
                event.player.getDisplayName(),
                uuid,
                remoteAddress.getHostName(),
                System.currentTimeMillis(),
                ConfigHandler.SERVER_UUID
            );
            sendPlayerDataToWebServer(playerInfo);

            if (ConfigHandler.DEBUG_MODE) FMLLog.info("Checking whitelist for player %s (%s) [Pre-Join]", player.getDisplayName(), uuid); 
            if (whitelistCache.isPlayerWhitelisted(uuid)) {
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("Player %s verified in local cache (pre-join check)", uuid);
                return; // Player is whitelisted in local cache (pre-join check)
            }

            if (ConfigHandler.DEBUG_MODE) FMLLog.info("Cache miss for %s, triggering synchronous remote check (pre-join)", uuid); 
            boolean isWhitelistedRemotely = webSocketClient.syncIsPlayerWhitelisted(uuid);

            if (isWhitelistedRemotely) {
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("Player %s verified after remote check (pre-join)", uuid); 
                return; 
            }

            FMLLog.warning("Player %s NOT on whitelist, denying login [Pre-Join]", player.getDisplayName()); 
            event.setCanceled(true); 
            event.setCancellationResult(new ChatComponentText("You are not on the whitelist.")); 
        }
    }


    private void sendPlayerDataToWebServer(PlayerInfo playerInfo) {
        if (playerInfo == null || webSocketClient == null) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.bigWarning("[Whitelistr] Error: PlayerInfo or WebSocketClient is null!");
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
            if (ConfigHandler.DEBUG_MODE) FMLLog.warning("WebSocket is not open, cannot send player data.");
        }
    }
}

