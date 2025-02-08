package eu.whitelistr.cache;

import eu.whitelistr.events.ConfigHandler;
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

        scheduler.scheduleAtFixedRate(() -> webSocketClient.sendCacheRequest(), 0,
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
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [SQL Cache] Loading SQL cache into Memory Cache...");
        try (Statement stmt = dbConnection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT uuid, username FROM whitelist")) {
            int loadedCount = 0;
            while (rs.next()) {
                memoryCache.put(rs.getString("uuid"), rs.getString("username"));
                loadedCount++;
            }
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Memory Cache] Loaded %d entries from SQL Cache to Memory Cache.", loadedCount);
        } catch (SQLException e) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.severe("[Whitelistr] [SQL Cache] Failed to load SQL cache into Memory Cache: %s", e.getMessage());
        }
    }


    public void updateWhitelist(Map<String, String> uuidToUsername) {
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Memory Cache] Updating Memory Cache with %d entries", uuidToUsername.size());
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Memory Cache] Current Memory Cache size: %d", memoryCache.size());
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Memory Cache] Current Memory Cache contents: %s", memoryCache.keySet());
        try {
            dbConnection.setAutoCommit(false);
            int batchCount = 0;
            for (Map.Entry<String, String> entry : uuidToUsername.entrySet()) {
                insertStatement.setString(1, entry.getKey());
                insertStatement.setString(2, entry.getValue());
                insertStatement.addBatch();
                batchCount++;
            }
            insertStatement.executeBatch();
            dbConnection.commit();
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [SQL Cache] Updated SQL Cache with %d entries.", batchCount);
            memoryCache.putAll(uuidToUsername);
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Memory Cache] Memory Cache updated.");
        } catch (SQLException e) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.severe("[Whitelistr] [SQL Cache] Failed to update SQL Cache: %s", e.getMessage());
            try { dbConnection.rollback(); } catch (SQLException ex) {}
        } finally {
            try { dbConnection.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    public void removeAllExcept(Set<String> uuidsToKeep) {
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Cache Sync] Synchronizing cache with %d active entries", uuidsToKeep.size());
        Set<String> originalUUIDs = new HashSet<>(memoryCache.keySet());
        Set<String> uuidsToRemove = new HashSet<>(originalUUIDs);
        uuidsToRemove.removeAll(uuidsToKeep);

        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Memory Cache] Removing %d expired entries from Memory Cache: %s", uuidsToRemove.size(), uuidsToRemove);

        try {
            memoryCache.keySet().removeAll(uuidsToRemove);
            dbConnection.setAutoCommit(false);
            try (PreparedStatement pstmt = dbConnection.prepareStatement(
                "DELETE FROM whitelist WHERE uuid = ?")) {

                int batchCount = 0;
                for (String uuid : uuidsToRemove) {
                    pstmt.setString(1, uuid);
                    pstmt.addBatch();
                    batchCount++;
                }
                int[] results = pstmt.executeBatch();
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [SQL Cache] Removed %d expired entries from SQL Cache.", batchCount);
                dbConnection.commit();
            }
        } catch (SQLException e) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.severe("[Whitelistr] [SQL Cache] SQL Cache synchronization failed: %s", e.getMessage());
            try { dbConnection.rollback(); } catch (SQLException ex) {}
        } finally {
            try { dbConnection.setAutoCommit(true); } catch (SQLException ex) {}
        }
    }

    public boolean isPlayerWhitelisted(String uuid) {
        boolean inCache = memoryCache.containsKey(uuid);
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] [Memory Cache] Checking Memory Cache for player %s, result: %s", uuid, inCache);
        return inCache;
    }


    public String getUsername(String uuid) {
        return memoryCache.getOrDefault(uuid, "");
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
            if (ConfigHandler.DEBUG_MODE) FMLLog.severe("[Whitelistr] [SQL Cache] Error shutting down SQL Cache: %s", e.getMessage());
        }
    }
}
