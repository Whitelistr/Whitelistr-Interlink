package eu.whitelistr.cache;

import eu.whitelistr.network.WClient;
import cpw.mods.fml.common.FMLLog;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Cache {
    private static final long CACHE_REFRESH_INTERVAL = 300_000L;
    private final Connection dbConnection;
    private final WClient webSocketClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectStatement;
    private final PreparedStatement deleteStatement;
    private final Map<String, String> memoryCache = new ConcurrentHashMap<>();
    private final AtomicBoolean refreshRequested = new AtomicBoolean(false);

    public Cache(Database database, WClient webSocketClient) throws SQLException {
        this.webSocketClient = webSocketClient;
        this.dbConnection = DriverManager.getConnection(database.getDbUrl());
        initializeDatabase();

        this.insertStatement = dbConnection.prepareStatement(
            "INSERT OR REPLACE INTO whitelist (uuid, username) VALUES (?, ?)");
        this.selectStatement = dbConnection.prepareStatement(
            "SELECT username FROM whitelist WHERE uuid = ?");
        this.deleteStatement = dbConnection.prepareStatement(
            "DELETE FROM whitelist WHERE uuid = ?");

        scheduler.scheduleAtFixedRate(this::refreshCache, 0,
            CACHE_REFRESH_INTERVAL, TimeUnit.MILLISECONDS);

        loadMemoryCache();
    }

    private void initializeDatabase() throws SQLException {
        try (Statement stmt = dbConnection.createStatement()) {
            stmt.execute("PRAGMA journal_mode = WAL");
            stmt.execute("PRAGMA synchronous = NORMAL");
            stmt.execute("PRAGMA optimize");
            stmt.execute("CREATE TABLE IF NOT EXISTS whitelist (uuid TEXT PRIMARY KEY, username TEXT)");
        }
    }

    private void loadMemoryCache() {
        try (Statement stmt = dbConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, username FROM whitelist")) {
            while (rs.next()) {
                memoryCache.put(rs.getString("uuid"), rs.getString("username"));
            }
        } catch (SQLException e) {
            FMLLog.severe("[Whitelistr] Failed to load memory cache: %s", e.getMessage());
        }
    }

    public void refreshCache() {
        FMLLog.info("Refreshing whitelist cache...");
        if (webSocketClient.isOpen()) {
            webSocketClient.send("{\"action\":\"sendCache\"}");
        } else {
            FMLLog.warning("[Whitelistr] WebSocket connection unavailable for cache refresh");
        }
    }

    public Set<String> getCachedUUIDs() {
        return Collections.unmodifiableSet(memoryCache.keySet());
    }


    public void updateWhitelist(Map<String, String> uuidToUsername) {
        FMLLog.info("[Whitelistr] Updating local whitelist cache with %d entries", uuidToUsername.size());
        try {
            dbConnection.setAutoCommit(false);
            for (Map.Entry<String, String> entry : uuidToUsername.entrySet()) {
                insertStatement.setString(1, entry.getKey());
                insertStatement.setString(2, entry.getValue());
                insertStatement.addBatch();
            }
            insertStatement.executeBatch();
            dbConnection.commit();
            memoryCache.putAll(uuidToUsername);
        } catch (SQLException e) {
            FMLLog.severe("[Whitelistr] Failed to update cache: %s", e.getMessage());
            try { dbConnection.rollback(); } catch (SQLException ex) {}
        } finally {
            try { dbConnection.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    public void removeAllExcept(Set<String> uuidsToKeep) {
        FMLLog.info("[Whitelistr] Synchronizing cache with %d active entries", uuidsToKeep.size());
        try {
            memoryCache.keySet().removeIf(uuid -> !uuidsToKeep.contains(uuid));
            dbConnection.setAutoCommit(false);
            try (PreparedStatement pstmt = dbConnection.prepareStatement(
                "DELETE FROM whitelist WHERE uuid = ?")) {

                Set<String> cachedUUIDs = new HashSet<>(getCachedUUIDs());
                cachedUUIDs.removeAll(uuidsToKeep);

                for (String uuid : cachedUUIDs) {
                    pstmt.setString(1, uuid);
                    pstmt.addBatch();
                }
                int[] results = pstmt.executeBatch();
                FMLLog.info("[Whitelistr] Removed %d expired entries", results.length);
                dbConnection.commit();
            }
        } catch (SQLException e) {
            FMLLog.severe("[Whitelistr] Cache synchronization failed: %s", e.getMessage());
            try { dbConnection.rollback(); } catch (SQLException ex) {}
        } finally {
            try { dbConnection.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    public boolean isPlayerWhitelisted(String uuid) {
        if (memoryCache.containsKey(uuid)) {
            FMLLog.info("[Whitelistr] Player %s found in memory cache", uuid);
            return true;
        }
        try {
            selectStatement.setString(1, uuid);
            try (ResultSet rs = selectStatement.executeQuery()) {
                if (rs.next()) {
                    String username = rs.getString("username");
                    memoryCache.put(uuid, username);
                    return true;
                }
            }
        } catch (SQLException e) {
            FMLLog.warning("[Whitelistr] Cache query failed for %s: %s", uuid, e.getMessage());
        }

        return checkRemoteWhitelist(uuid);
    }

    private boolean checkRemoteWhitelist(String uuid) {
        FMLLog.info("[Whitelistr] Checking remote whitelist for %s", uuid);
        if (!refreshRequested.getAndSet(true)) {
            FMLLog.info("[Whitelistr] Requesting full cache refresh for missing UUID: %s", uuid);
            refreshCache();
        }
        boolean isWhitelisted = webSocketClient.syncIsPlayerWhitelisted(uuid);
        if (isWhitelisted) {
            cacheRemotePlayer(uuid);
        }
        return isWhitelisted;
    }

    private void cacheRemotePlayer(String uuid) {
        String username = webSocketClient.getUsernameFromUUID(uuid);
        if (username != null) {
            Map<String, String> update = new HashMap<>();
            update.put(uuid, username);
            updateWhitelist(update);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
            insertStatement.close();
            selectStatement.close();
            deleteStatement.close();
            dbConnection.close();
        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] Error shutting down SQL cache: %s", e.getMessage());
        }
    }
}
