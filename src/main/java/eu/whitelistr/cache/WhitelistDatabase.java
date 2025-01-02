package eu.whitelistr.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public class WhitelistDatabase {

    private static final String DB_FILE_NAME = "whitelist_cache.db";
    private static final String DB_URL;
    private static final String SQLITE_DRIVER_URL = "https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.46.0.0/sqlite-jdbc-3.46.0.0.jar";
    private static final String DRIVER_CLASS = "org.sqlite.JDBC";

    static {
        File whitelistrDir = new File("Whitelistr");
        if (!whitelistrDir.exists()) {
            whitelistrDir.mkdir();
        }
        DB_URL = "jdbc:sqlite:" + new File(whitelistrDir, DB_FILE_NAME).getAbsolutePath();
        loadSQLiteDriver();
    }

    public WhitelistDatabase() {
        initializeDatabase();
    }

    private static void loadSQLiteDriver() {
        try {
            File driverFile = new File("Whitelistr/sqlite-jdbc-3.46.0.jar");
            if (!driverFile.exists()) {
                System.out.println("Downloading SQLite JDBC driver...");
                try (InputStream in = new URL(SQLITE_DRIVER_URL).openStream();
                     FileOutputStream out = new FileOutputStream(driverFile)) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                }
                System.out.println("SQLite JDBC driver downloaded successfully.");
            }
            URLClassLoader classLoader = new URLClassLoader(new URL[]{driverFile.toURI().toURL()});
            Class<?> driverClass = Class.forName(DRIVER_CLASS, true, classLoader);
            Driver driverInstance = (Driver) driverClass.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(new Driver() {
                @Override
                public Connection connect(String url, java.util.Properties info) throws SQLException {
                    return driverInstance.connect(url, info);
                }

                @Override
                public boolean acceptsURL(String url) throws SQLException {
                    return driverInstance.acceptsURL(url);
                }

                @Override
                public DriverPropertyInfo[] getPropertyInfo(String url, java.util.Properties info) throws SQLException {
                    return driverInstance.getPropertyInfo(url, info);
                }

                @Override
                public int getMajorVersion() {
                    return driverInstance.getMajorVersion();
                }

                @Override
                public int getMinorVersion() {
                    return driverInstance.getMinorVersion();
                }

                @Override
                public boolean jdbcCompliant() {
                    return driverInstance.jdbcCompliant();
                }

                @Override
                public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
                    return driverInstance.getParentLogger();
                }
            });

            System.out.println("SQLite JDBC driver loaded and registered successfully.");
        } catch (Exception e) {
            throw new RuntimeException("Failed to load SQLite JDBC driver.", e);
        }
    }
    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            String createTableSQL = "CREATE TABLE IF NOT EXISTS whitelist (" +
                "username TEXT PRIMARY KEY NOT NULL);";
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    public void updateCache(Set<String> whitelistedPlayers) {
        String insertSQL = "INSERT OR REPLACE INTO whitelist (username) VALUES (?)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(insertSQL)) {

            for (String player : whitelistedPlayers) {
                stmt.setString(1, player);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            System.err.println("Failed to update cache: " + e.getMessage());
        }
    }
    public boolean isPlayerWhitelisted(String playerName) {
        String querySQL = "SELECT username FROM whitelist WHERE username = ?";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement stmt = conn.prepareStatement(querySQL)) {

            stmt.setString(1, playerName);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            System.err.println("Failed to check whitelist status: " + e.getMessage());
            return false;
        }
    }
    public Set<String> getWhitelistedPlayers() {
        Set<String> players = new HashSet<>();
        String querySQL = "SELECT username FROM whitelist";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(querySQL)) {

            while (rs.next()) {
                players.add(rs.getString("username"));
            }
        } catch (SQLException e) {
            System.err.println("Failed to retrieve whitelisted players: " + e.getMessage());
        }
        return players;
    }
}



