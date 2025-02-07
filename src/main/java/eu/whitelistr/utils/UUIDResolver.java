package eu.whitelistr.utils;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.FMLLog;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class UUIDResolver {
    private static final int CACHE_SIZE = 1000;
    private static final long CACHE_DURATION = 6 * 60 * 60 * 1000; // 6 hours
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final Map<String, CacheEntry> uuidCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry> usernameCache = new ConcurrentHashMap<>();

    public CompletableFuture<Map<String, String>> resolveUUIDToUsernameAsync(String uuid) {
        return CompletableFuture.supplyAsync(() -> resolveWithCache(uuid), executor);
    }

    private Map<String, String> resolveWithCache(String uuid) {
        CacheEntry cached = uuidCache.get(uuid);
        if (cached != null && !cached.isExpired()) {
            return cached.data;
        }

        try {
            URL url = new URL("https://playerdb.co/api/player/minecraft/" + uuid);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Whitelistr/1.0 (avalanche752@gmail.com)");

            try (InputStreamReader reader = new InputStreamReader(conn.getInputStream())) {
                JsonObject jsonResponse = new JsonParser().parse(reader).getAsJsonObject();
                if (jsonResponse.has("data")) {
                    JsonObject data = jsonResponse.getAsJsonObject("data");
                    if (data.has("player")) {
                        JsonObject player = data.getAsJsonObject("player");
                        if (player.has("username") && player.has("id") && player.has("raw_id")) {
                            Map<String, String> result = new HashMap<>();
                            result.put("username", player.get("username").getAsString());
                            result.put("fullUUID", player.get("id").getAsString());
                            result.put("trimmedUUID", player.get("raw_id").getAsString());
                            result = Collections.unmodifiableMap(result);
                            updateCache(uuid, result);
                            return result;
                        }
                    }
                }
            }
        } catch (Exception e) {
            FMLLog.warning("[Whitelistr] UUID resolution failed for %s: %s", uuid, e.getMessage());
        }
        return null;
    }

    private void updateCache(String uuid, Map<String, String> data) {
        if (uuidCache.size() >= CACHE_SIZE) uuidCache.clear();
        uuidCache.put(uuid, new CacheEntry(data));

        String username = data.get("username");
        if (username != null && usernameCache.size() < CACHE_SIZE) {
            usernameCache.put(username, new CacheEntry(data));
        }
    }

    private static class CacheEntry {
        final Map<String, String> data;
        final long expiration;

        CacheEntry(Map<String, String> data) {
            this.data = data;
            this.expiration = System.currentTimeMillis() + CACHE_DURATION;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiration;
        }
    }
}
