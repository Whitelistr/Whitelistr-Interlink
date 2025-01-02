package eu.whitelistr.network;

import com.google.gson.Gson;
import eu.whitelistr.cache.WhitelistCache;
import eu.whitelistr.cache.WhitelistDatabase;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Map;
import java.util.HashMap;

import static eu.whitelistr.events.ConfigHandler.SERVER_UUID;
import static eu.whitelistr.events.ConfigHandler.API_KEY;

public class WClient extends WebSocketClient {

    public static final Gson gson = new Gson();
    private boolean reconnecting = false;
    private final WhitelistCache whitelistCache;
    private static final int MAX_RETRY_ATTEMPTS = 5;

    public WClient(String serverUri, String serverUUID, String apiKey) throws URISyntaxException {
        super(new URI(serverUri));
        this.whitelistCache = new WhitelistCache(new WhitelistDatabase(), this);
    }

    public Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("x-api-key", API_KEY);
        headers.put("x-server-uuid", SERVER_UUID);
        return headers;
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        System.out.println("Connected to WebSocket server");
        reconnecting = false;
        sendCacheRequest();
    }

    @Override
    public void onMessage(String message) {
        Event event = gson.fromJson(message, Event.class);
        handleEvent(event);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Disconnected from WebSocket server: " + reason);
        reconnect();
    }

    @Override
    public void onError(Exception ex) {
        ex.printStackTrace();
    }

    public void reconnect() {
        if (!reconnecting) {
            reconnecting = true;
            System.out.println("Attempting to reconnect...");
            new Thread(() -> {
                int attemptCount = 0;
                while (attemptCount < MAX_RETRY_ATTEMPTS) {
                    try {
                        Thread.sleep((long) (Math.pow(2, attemptCount) * 1000));
                        this.reconnectBlocking();
                        System.out.println("Reconnection successful.");
                        reconnecting = false;
                        break;
                    } catch (InterruptedException e) {
                        System.err.println("Reconnection attempt interrupted: " + e.getMessage());
                        Thread.currentThread().interrupt();
                    } catch (Exception e) {
                        System.err.println("Reconnection failed: " + e.getMessage());
                    }
                    attemptCount++;
                }

                if (attemptCount >= MAX_RETRY_ATTEMPTS) {
                    System.err.println("Max reconnection attempts reached. Unable to reconnect.");
                }
                reconnecting = false;
            }).start();
        }
    }

    public void sendCacheRequest() {
        this.send("{\"action\":\"sendCache\"}");
    }

    private void handleEvent(Event event) {
        if (event.getServerId().equals(SERVER_UUID)) {
            System.out.println("Valid event received for server: " + SERVER_UUID);
            whitelistUser(event.getUsername());
        } else {
            System.out.println("Received event for a different server: " + event.getServerId());
        }
    }
    public boolean isPlayerWhitelisted(String playerName) {
        if (this.isOpen()) {
            System.out.println("Checking whitelist via WebSocket...");
            return false;
        } else {
            System.out.println("WebSocket unavailable, falling back to cache...");
            return whitelistCache.isPlayerWhitelisted(playerName);
        }
    }

    private void whitelistUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            System.err.println("Invalid username received. Skipping whitelisting.");
            return;
        }
        System.out.println("Whitelisting user: " + username);
        whitelistCache.updateWhitelist(Collections.singleton(username));
    }

    private static class Event {
        private String username;
        private String serverUUID;

        public String getUsername() {
            return username;
        }

        public String getServerId() {
            return serverUUID;
        }
    }
}


