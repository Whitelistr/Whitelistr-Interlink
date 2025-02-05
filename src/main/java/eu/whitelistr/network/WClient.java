package eu.whitelistr.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.cache.Database;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Map;
import java.util.HashMap;

import static eu.whitelistr.events.ConfigHandler.SERVER_UUID;

public class WClient extends WebSocketClient {

    public static final Gson gson = new Gson();
    private boolean reconnecting = false;
    private final Cache whitelistCache;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private WebSocketResponseCallback callback;

    public WClient(String serverUri, String serverUUID, String apiKey) throws URISyntaxException {
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
        System.out.println("Connected to WebSocket server");
        reconnecting = false;
        sendCacheRequest();
    }

    public interface WebSocketResponseCallback {
        void onResponse(boolean isWhitelisted);
    }


    @Override
    public void onMessage(String message) {
        Event event = gson.fromJson(message, Event.class);
        handleEvent(event);

        // Handle whitelist response
        JsonObject jsonResponse = new JsonParser().parse(message).getAsJsonObject();
        if (jsonResponse.has("action") && jsonResponse.get("action").getAsString().equals("isWhitelisted")) {
            boolean isWhitelisted = jsonResponse.get("isWhitelisted").getAsBoolean();
            if (callback != null) {
                callback.onResponse(isWhitelisted);
                callback = null; // Clear the callback after use
            }
        }
    }

    public void setWebSocketResponseCallback(WebSocketResponseCallback callback) {
        this.callback = callback;
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
                        System.out.println("Reconnection initiated.");
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
        if (event.getServerId() != null && event.getServerId().equals(SERVER_UUID)) {
            System.out.println("Valid event received for server: " + SERVER_UUID);
            String username = UUIDConvert(event.getUuid());
            if (username != null) {
                whitelistUser(username);
            } else {
                System.err.println("Failed to convert UUID to username for: " + event.getUuid());
            }
        } else {
            if (event.getServerId() == null) {
                System.err.println("Received event with null serverId");
            } else {
                System.out.println("Received event for a different server: " + event.getServerId());
            }
        }
    }


    public boolean syncIsPlayerWhitelisted(String uuid) {
        if (!this.isOpen()) return false;

        final Object lock = new Object();
        final boolean[] result = {false};

        setWebSocketResponseCallback(isWhitelisted -> {
            synchronized (lock) {
                result[0] = isWhitelisted;
                lock.notifyAll();
            }
        });

        JsonObject request = new JsonObject();
        request.addProperty("action", "isWhitelisted");
        request.addProperty("uuid", uuid);
        send(request.toString());

        synchronized (lock) {
            try {
                lock.wait(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return result[0];
    }

    public String getUsernameFromUUID(String uuid) {
        // Implement cached UUID to username lookup if possible
        return UUIDConvert(uuid);
    }

    private void whitelistUser(String uuid) {
        if (uuid == null || uuid.isEmpty()) {
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


