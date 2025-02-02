package eu.whitelistr.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigHandler {

    private static final String CONFIG_FOLDER = "Whitelistr";
    private static final String CONFIG_FILE_NAME = "whitelistr.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String API_KEY = "";
    public static String SERVER_UUID = "";
    public static String WEBSOCKET_URL = "wss:/app.whitelistr.space";

    private static File configFile;

    public static class Config {
        public String api_key = "";
        public String server_uuid = "";
        public String websocket_url = "";
    }

    public static void loadConfig() {
        try {
            File configDir = new File(CONFIG_FOLDER);
            if (!configDir.exists()) {
                configDir.mkdir();
            }

            configFile = new File(configDir, CONFIG_FILE_NAME);
            if (!configFile.exists()) {
                copyDefaultConfig();
            }

            try (Reader reader = new FileReader(configFile)) {
                Config config = GSON.fromJson(reader, Config.class);

                API_KEY = config.api_key;
                SERVER_UUID = config.server_uuid;
                WEBSOCKET_URL = config.websocket_url.isEmpty() ? WEBSOCKET_URL : config.websocket_url;

                if (API_KEY.isEmpty() || SERVER_UUID.isEmpty()) {
                    throw new IllegalStateException("API Key or Server UUID is missing in the configuration file.");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load the configuration file.", e);
        }
    }

    private static void copyDefaultConfig() {
        try (InputStream inputStream = ConfigHandler.class.getClassLoader().getResourceAsStream(CONFIG_FILE_NAME)) {
            if (inputStream == null) {
                System.err.println("Default configuration not found in the JAR. Creating an empty configuration file.");
                saveConfig(); 
                return;
            }
            Path configPath = configFile.toPath();
            Files.createDirectories(configPath.getParent());
            Files.copy(inputStream, configPath, StandardCopyOption.REPLACE_EXISTING);

            System.out.println("Copied default configuration from JAR to " + CONFIG_FOLDER + " folder.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to copy the default configuration file.", e);
        }
    }

    public static void saveConfig() {
        try (Writer writer = new FileWriter(configFile)) {
            Config config = new Config();
            config.api_key = API_KEY;
            config.server_uuid = SERVER_UUID;
            config.websocket_url = WEBSOCKET_URL;

            GSON.toJson(config, writer);
            System.out.println("Configuration saved successfully.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to save the configuration file.", e);
        }
    }
}
