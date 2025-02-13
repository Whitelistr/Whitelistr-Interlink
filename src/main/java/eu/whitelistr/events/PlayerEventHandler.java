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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PlayerEventHandler {

    private final WClient webSocketClient;
    private final Cache whitelistCache;
    private final ScheduledExecutorService delayedWhitelistCheckExecutor = Executors.newSingleThreadScheduledExecutor();

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
            String uuid = player.getUniqueID().toString().replace("-", "");
            String playerName = player.getDisplayName();

            PlayerInfo playerInfo = new PlayerInfo(
                playerIP,
                playerName,
                uuid,
                remoteAddress.getHostName(),
                System.currentTimeMillis(),
                ConfigHandler.SERVER_UUID
            );
            sendPlayerDataToWebServer(playerInfo);

            if (ConfigHandler.DEBUG_MODE) FMLLog.info("Checking whitelist for player %s (%s)", playerName, uuid);

            if (whitelistCache.isPlayerWhitelisted(uuid)) {
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("Player %s verified in local cache (initial check)", playerName);
                return;
            }

            if (ConfigHandler.DEBUG_MODE) FMLLog.info("Cache miss for %s (%s), triggering asynchronous cache refresh and delayed whitelist check", playerName, uuid);

            webSocketClient.sendCacheRequest();

            delayedWhitelistCheckExecutor.schedule(() -> {
                performDelayedWhitelistCheck(player, uuid, playerName);
            }, 2, TimeUnit.SECONDS);
        }
    }

    private void performDelayedWhitelistCheck(EntityPlayerMP player, String uuid, String playerName) {
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("Delayed whitelist check for %s (%s) started...", playerName, uuid);

        if (whitelistCache.isPlayerWhitelisted(uuid)) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("Delayed check: Player %s (%s) verified in local cache after delay", playerName, uuid);
            return;
        }

        FMLLog.warning("Delayed check: Player %s (%s) NOT on whitelist, kicking...", playerName, uuid);
        player.playerNetServerHandler.kickPlayerFromServer("You are not on a Whitelist");
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

    public void shutdown() {
        delayedWhitelistCheckExecutor.shutdownNow();
    }
}
