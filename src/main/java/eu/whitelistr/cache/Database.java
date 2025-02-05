package eu.whitelistr.cache;

import java.io.File;
import java.sql.*;

public class Database {

    private static final String DB_FILE_NAME = "whitelist_cache.db";
    private static final String DB_URL;

    static {
        File whitelistrDir = new File("Whitelistr");
        if (!whitelistrDir.exists()) {
            whitelistrDir.mkdir();
        }
        DB_URL = "jdbc:sqlite:" + new File(whitelistrDir, DB_FILE_NAME).getAbsolutePath();
    }

    public Database() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            String createTableSQL = "CREATE TABLE IF NOT EXISTS whitelist (" +
                "uuid TEXT PRIMARY KEY NOT NULL, " +
                "username TEXT NOT NULL);";
            stmt.execute(createTableSQL);
        } catch (SQLException e) {
            System.err.println("Failed to initialize database: " + e.getMessage());
        }
    }

    public static String getDbUrl() {
        return DB_URL;
    }
}
