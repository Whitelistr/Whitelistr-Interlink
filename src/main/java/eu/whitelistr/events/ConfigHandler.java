package eu.whitelistr.events;

import com.google.gson.Gson;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigHandler {

    public static String API_KEY = "";
    public static String SERVER_UUID = "";
    public static String WEBSOCKET_URL = "wss:/app.whitelistr.space";
    public static boolean DEBUG_MODE = false;
    private static final Gson gson = new Gson();
    private static File whitelistrDir;
    private static File configFile;

    public static class Config {
        public String x_api_key = "";
        public String x_server_uuid = "";
        public String websocket_url = "wss:/app.whitelistr.space";
        public boolean debug = false;
    }
    public static void loadConfig() {
        whitelistrDir = new File("Whitelistr");
        if (!whitelistrDir.exists()) {
            whitelistrDir.mkdir();
        }

        configFile = new File(whitelistrDir, "whitelistr.json");

        if (!configFile.exists()) {
            copyDefaultConfig();
        }

        try (Reader reader = new FileReader(configFile)) {
            Config config = gson.fromJson(reader, Config.class);

            API_KEY = config.x_api_key;
            SERVER_UUID = config.x_server_uuid;
            if (config.websocket_url != null && !config.websocket_url.isEmpty()) {
                WEBSOCKET_URL = config.websocket_url;
            }
            DEBUG_MODE = config.debug;
            if (API_KEY.isEmpty() || SERVER_UUID.isEmpty()) {
                throw new RuntimeException("API Key or Server UUID is missing in configuration.");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static void copyDefaultConfig() {
        try (InputStream inputStream = ConfigHandler.class.getClassLoader().getResourceAsStream("whitelistr.json")) {
            if (inputStream == null) {
                System.err.println("Default config not found in the JAR. Creating an empty config.");
                configFile.getParentFile().mkdirs();
                return;
            }
            Path configPath = configFile.toPath();
            Files.createDirectories(configPath.getParent());
            Files.copy(inputStream, configPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Copied default config from JAR to Whitelistr folder.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
