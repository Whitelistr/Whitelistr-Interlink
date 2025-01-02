package eu.whitelistr.cache;

import eu.whitelistr.network.WClient;

import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class WhitelistCache {

    private static final long CACHE_REFRESH_INTERVAL = 15 * 60 * 1000L; // 15 minutes?
    private final WhitelistDatabase database;
    private final WClient webSocketClient;

    public WhitelistCache(WhitelistDatabase database, WClient webSocketClient) {
        this.database = database;
        this.webSocketClient = webSocketClient;
        scheduleCacheRefresh();
    }
    private void scheduleCacheRefresh() {
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                refreshCache();
            }
        }, 0, CACHE_REFRESH_INTERVAL);
    }
    private void refreshCache() {
        System.out.println("Refreshing whitelist cache...");
        if (webSocketClient.isOpen()) {
            webSocketClient.send("{\"action\":\"sendCache\"}");
        } else {
            System.err.println("Cannot refresh cache. WebSocket connection is unavailable.");
        }
    }
    public void updateWhitelist(Map<String, String> uuidToUsername) {
        System.out.println("Updating local whitelist cache...");
        database.updateCache(uuidToUsername);
    }
    public boolean isPlayerWhitelisted(String playerName) {
        return database.isPlayerWhitelisted(playerName);
    }
}
