package eu.whitelistr.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.whitelistr.cache.WhitelistCache;
import eu.whitelistr.cache.WhitelistDatabase;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import scala.util.parsing.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
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
        this.addHeader("x-api-key", API_KEY);
        this.addHeader("x-server-uuid", SERVER_UUID);

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
            String username = UUIDConvert(event.getUuid());
            if (username != null) {
                whitelistUser(username);
            } else {
                System.err.println("Failed to convert UUID to username for: " + event.getUuid());
            }
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

    private void whitelistUser(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            System.err.println("Invalid UUID received. Skipping whitelisting.");
            return;
        }
        String username = UUIDConvert(uuid);
        if (username != null) {
            System.out.println("Whitelisting user: " + username);
            Map<String, String> uuidToUsername = new HashMap<>();
            uuidToUsername.put(uuid, username);
            whitelistCache.updateWhitelist(uuidToUsername);
        } else {
            System.err.println("Failed to convert UUID to username for: " + uuid);
        }
    }

    private String UUIDConvert(String uuid) {
        try {
            URL url = new URL("https://api.mojang.com/user/profiles/" + uuid.replace("-", "") + "/names");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream());
                 BufferedReader bufferedReader = new BufferedReader(reader)) {
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    response.append(line);
                }
                JsonArray names = JsonParser.parseString(response.toString()).getAsJsonArray();
                if (names.size() > 0) {
                    JsonObject latestName = names.get(names.size() - 1).getAsJsonObject();
                    return latestName.get("name").getAsString();
                }
                return null;
            }
        } catch (Exception e) {
            System.err.println("Failed to resolve username for UUID " + uuid + ": " + e.getMessage());
            return null;
        }
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


