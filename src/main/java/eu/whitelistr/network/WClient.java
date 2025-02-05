package eu.whitelistr.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.FMLLog;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.cache.Database;
import eu.whitelistr.utils.UUIDResolver;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;


import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.*;

import static eu.whitelistr.events.ConfigHandler.SERVER_UUID;

public class WClient extends WebSocketClient {
    public static final Gson gson = new Gson();

    private final Cache whitelistCache;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
    private final UUIDResolver uuidResolver = new UUIDResolver();
    private final Object connectionLock = new Object();
    private volatile boolean isRunning = true;

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
        FMLLog.info("WebSocket connection established");
        sendCacheRequest();
    }

    @Override
    public void onMessage(String message) {
        JsonObject jsonResponse = new JsonParser().parse(message).getAsJsonObject();
        handleMessageAction(jsonResponse);
    }

    private void handleMessageAction(JsonObject json) {
        if (json.has("action")) {
            String action = json.get("action").getAsString();
            if ("isWhitelisted".equals(action)) {
                handleWhitelistResponse(json);
            } else if ("sendCache".equals(action)) {
                handleCacheUpdate(json);
            }
        }
    }

    private void handleWhitelistResponse(JsonObject json) {
        boolean isWhitelisted = json.get("isWhitelisted").getAsBoolean();
        synchronized (connectionLock) {
            connectionLock.notifyAll();
        }
    }

    private void handleCacheUpdate(JsonObject json) {
        json.getAsJsonArray("whitelistedPlayers").forEach(element -> {
            String uuid = element.getAsString();
            whitelistUser(uuid);
        });
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        FMLLog.warning("WebSocket closed: %s (code %d)", reason, code);
        if (isRunning) scheduleReconnect();
    }

    private void scheduleReconnect() {
        reconnectExecutor.schedule(() -> {
            try {
                FMLLog.info("Attempting WebSocket reconnection...");
                this.reconnectBlocking();
            } catch (Exception e) {
                FMLLog.warning("Reconnection failed: %s", e.getMessage());
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
        this.send("{\"action\":\"sendCache\"}");
    }

    public boolean syncIsPlayerWhitelisted(String uuid) {
        if (!isOpen()) return false;

        JsonObject request = new JsonObject();
        request.addProperty("action", "isWhitelisted");
        request.addProperty("uuid", uuid);
        send(request.toString());

        synchronized (connectionLock) {
            try {
                connectionLock.wait(3000);
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    public String getUsernameFromUUID(String uuid) {
        CompletableFuture<Map<String, String>> future = uuidResolver.resolveUUIDToUsernameAsync(uuid);
        try {
            Map<String, String> userInfo = future.get(2, TimeUnit.SECONDS); // Timeout after 2 seconds
            return userInfo != null ? userInfo.get("username") : null;
        } catch (Exception e) {
            FMLLog.warning("Failed to resolve username for UUID %s: %s", uuid, e.getMessage());
            return null;
        }
    }

    private void whitelistUser(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
            FMLLog.warning("Invalid UUID received");
            return;
        }

        uuidResolver.resolveUUIDToUsernameAsync(uuid).thenAccept(userInfo -> {
            if (userInfo != null) {
                String username = userInfo.get("username");
                String fullUUID = userInfo.get("fullUUID");

                if (fullUUID != null && username != null) {
                    FMLLog.info("Whitelisting user: %s (%s)", username, fullUUID);
                    Map<String, String> uuidToUsername = Collections.singletonMap(fullUUID, username);
                    whitelistCache.updateWhitelist(uuidToUsername);
                } else {
                    FMLLog.warning("Invalid user data for UUID: %s", uuid);
                }
            } else {
                FMLLog.warning("Failed to resolve UUID: %s", uuid);
            }
        });
    }

    private static class Event {
        private String uuid;
        private String serverUUID;
        private String username;

        public String getUuid() {
            return uuid;
        }

        public String getServerId() {
            return serverUUID;
        }

        public String getUsername() {
            return username;
        }
    }
}


