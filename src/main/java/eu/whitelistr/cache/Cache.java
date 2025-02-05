package eu.whitelistr.cache;

import eu.whitelistr.network.WClient;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Cache {

    private static final long CACHE_REFRESH_INTERVAL = 5 * 60 * 1000L; //5 minutes?
    private final Database database;
    private final WClient webSocketClient;

    public Cache(Database database, WClient webSocketClient) {
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
        String dbUrl = Database.getDbUrl();
        String insertSQL = "INSERT OR REPLACE INTO whitelist (uuid, username) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {

            for (Map.Entry<String, String> entry : uuidToUsername.entrySet()) {
                stmt.setString(1, entry.getKey());
                stmt.setString(2, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("Failed to update cache: " + e.getMessage());
        }
    }

    public boolean isPlayerWhitelisted(String uuid) {
        // Check local cache first
        System.out.println("Checking local cache for player: " + uuid);
        String dbUrl = Database.getDbUrl();
        String querySQL = "SELECT uuid FROM whitelist WHERE uuid = ?";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             PreparedStatement stmt = conn.prepareStatement(querySQL)) {
            stmt.setString(1, uuid);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Database query failed: " + e.getMessage());
        }

        // Fallback to WebSocket check if not in local cache
        System.out.println("Checking WebSocket for player: " + uuid);
        boolean isWhitelisted = webSocketClient.syncIsPlayerWhitelisted(uuid);
        if (isWhitelisted) {
            // Update cache with new entry
            Map<String, String> update = new HashMap<>();
            String username = webSocketClient.getUsernameFromUUID(uuid);
            if (username != null) {
                update.put(uuid, username);
                updateWhitelist(update);
            }
        }
        return isWhitelisted;
    }

    public Map<String, String> getWhitelistedPlayers() {
        Map<String, String> players = new HashMap<>();
        String dbUrl = Database.getDbUrl();
        String querySQL = "SELECT uuid, username FROM whitelist";
        try (Connection conn = DriverManager.getConnection(dbUrl);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                players.put(rs.getString("uuid"), rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to retrieve whitelisted players: " + e.getMessage());
        }
        return players;
    }
}

