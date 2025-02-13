package eu.whitelistr.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.FMLLog;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.cache.Database;
import eu.whitelistr.events.ConfigHandler;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class WClient extends WebSocketClient {
    public static final Gson gson = new Gson();

    private final Cache whitelistCache;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Object connectionLock = new Object();
    private volatile boolean isRunning = true;
    private volatile boolean cacheUpdated = false;

    public WClient(String serverUri, String serverUUID, String apiKey) throws Exception {
        super(new URI(serverUri), buildHeaders(serverUUID, apiKey));
        this.whitelistCache = new Cache(new Database(), this);
    }

    private static Map<String, String> buildHeaders(String serverUUID, String apiKey) {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", apiKey);
        headers.put("x-server-uuid", serverUUID);
        return headers;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] WebSocket connection established");
        sendCacheRequest();
    }

    @Override
    public void onMessage(String message) {
        FMLLog.info("[Whitelistr] WebSocket onMessage RAW message: " + message);
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] WebSocket onMessage received at: " + Instant.now());
        JsonObject jsonResponse = new JsonParser().parse(message).getAsJsonObject();
        FMLLog.info("[Whitelistr] WebSocket onMessage parsed JSON: " + jsonResponse.toString());
        handleMessageAction(jsonResponse);
    }

    private void handleMessageAction(JsonObject jsonResponse) {
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] handleMessageAction called at: " + Instant.now() + ", json: " + jsonResponse.toString());
        if (jsonResponse.has("action")) {
            String action = jsonResponse.get("action").getAsString();
            if ("sendCache".equals(action)) {
                handleCacheUpdate(jsonResponse);
            }
        } else if (jsonResponse.has("whitelistedPlayers")) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Direct whitelistedPlayers payload detected, handling cache update.");
            handleCacheUpdate(jsonResponse);
        } else {
            if (ConfigHandler.DEBUG_MODE) FMLLog.warning("[Whitelistr] Unknown message format received: " + jsonResponse.toString());
        }
    }

    private void handleCacheUpdate(JsonObject jsonResponse) {
        FMLLog.info("[Whitelistr] handleCacheUpdate - JSON Payload: " + jsonResponse.toString());
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] handleCacheUpdate started at: " + Instant.now() + ", json: " + jsonResponse.toString());
        cacheUpdated = false;
        Set<String> currentWhitelist = new HashSet<>();
        if(jsonResponse.has("whitelistedPlayers") && jsonResponse.get("whitelistedPlayers").isJsonArray()) {
            jsonResponse.getAsJsonArray("whitelistedPlayers").forEach(element -> {
                currentWhitelist.add(element.getAsString());
            });
            FMLLog.info("[Whitelistr] handleCacheUpdate - currentWhitelist before removeAllExcept: " + currentWhitelist);
            whitelistCache.removeAllExcept(currentWhitelist);
            Map<String, String> usernameCache = new HashMap<>();
            currentWhitelist.parallelStream().forEach(uuid -> {
                if (!whitelistCache.isPlayerWhitelisted(uuid)) {
                    usernameCache.put(uuid, "");
                }
            });
            FMLLog.info("[Whitelistr] handleCacheUpdate - usernameCache before updateWhitelist: " + usernameCache);
            whitelistCache.updateWhitelist(usernameCache);
            cacheUpdated = true;
            FMLLog.info("[Whitelistr] handleCacheUpdate - cacheUpdated set to true");
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Cache updated flag set to true at: " + Instant.now());
            synchronized (connectionLock) {
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Notifying connectionLock at: " + Instant.now());
                connectionLock.notifyAll();
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Notification sent at: " + Instant.now());
            }
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] handleCacheUpdate finished and notified at: " + Instant.now());
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Cache update processed and notified.");
        } else {
            if (ConfigHandler.DEBUG_MODE) FMLLog.warning("[Whitelistr] handleCacheUpdate: whitelistedPlayers array not found or invalid in JSON: " + jsonResponse.toString());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        if (ConfigHandler.DEBUG_MODE) FMLLog.warning("WebSocket closed: %s (code %d)", reason, code);
        if (isRunning) scheduleReconnect();
    }

    private void scheduleReconnect() {
        reconnectExecutor.schedule(() -> {
            try {
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("Attempting WebSocket reconnection...");
                this.reconnectBlocking();
            } catch (Exception e) {
                if (ConfigHandler.DEBUG_MODE) FMLLog.warning("Reconnection failed: %s", e.getMessage());
                if (isRunning) scheduleReconnect();
            }
        }, calculateBackoffDelay(), TimeUnit.MILLISECONDS);
    }

    private long calculateBackoffDelay() {
        return ThreadLocalRandom.current().nextLong(1000, 10000);
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void shutdown() {
        isRunning = false;
        reconnectExecutor.shutdownNow();
        close();
    }

    public void sendCacheRequest() {
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("Requesting cache update from WebSocket server...");
        this.send("{\"action\":\"sendCache\"}");
    }

    public boolean syncIsPlayerWhitelisted(String uuid) {
        if (!isOpen()) return false;
        cacheUpdated = false;
        JsonObject request = new JsonObject();
        request.addProperty("action", "sendCache");
        send(request.toString());

        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] syncIsPlayerWhitelisted - Waiting on connectionLock at: " + Instant.now());
        synchronized (connectionLock) {
            try {
                connectionLock.wait(10000);
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] syncIsPlayerWhitelisted - Wait finished at: " + Instant.now());
                if (cacheUpdated) {
                    boolean isWhitelisted = whitelistCache.isPlayerWhitelisted(uuid);
                    if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] syncIsPlayerWhitelisted - Cache updated, player whitelisted status: " + isWhitelisted + " at: " + Instant.now());
                    return isWhitelisted;
                } else {
                    if (ConfigHandler.DEBUG_MODE) FMLLog.warning("[Whitelistr] syncIsPlayerWhitelisted - Timeout before cache update at: " + Instant.now());
                    return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (ConfigHandler.DEBUG_MODE) FMLLog.warning("[Whitelistr] syncIsPlayerWhitelisted - Interrupted during wait at: " + Instant.now());
                return false;
            }
        }
    }
}
