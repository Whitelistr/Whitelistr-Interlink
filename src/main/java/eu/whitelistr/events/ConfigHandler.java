package eu.whitelistr.events;

import com.google.gson.Gson;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class ConfigHandler {

    public static String API_KEY = "";
    public static String SERVER_UUID = "";
    public static String WEBSOCKET_URL = "wss://api.whitelistr.space/interlink";
    private static final Gson gson = new Gson();
    private static File whitelistrDir;
    private static File configFile;

    public static class Config {
        public String x_api_key = "";
        public String x_server_uuid = "";
        public String websocket_url = "";
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
            if (!config.websocket_url.isEmpty()) {
                WEBSOCKET_URL = config.websocket_url;
            }
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
    public static void saveConfig() {
        try (Writer writer = new FileWriter(configFile)) {
            Config config = new Config();
            config.x_api_key = API_KEY;
            config.x_server_uuid = SERVER_UUID;
            config.websocket_url = WEBSOCKET_URL;
            gson.toJson(config, writer);

            System.out.println("Config saved successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
