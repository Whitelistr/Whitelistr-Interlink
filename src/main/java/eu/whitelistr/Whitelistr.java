package eu.whitelistr;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.FMLLog;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.cache.Database;
import eu.whitelistr.events.ConfigHandler;
import eu.whitelistr.events.PlayerEventHandler;
import eu.whitelistr.network.WClient;
import eu.whitelistr.utils.Libraries;
import net.minecraft.server.MinecraftServer;

@Mod(modid = Whitelistr.MODID, version = "1.0", name = "Whitelistr", acceptableRemoteVersions = "*")
public class Whitelistr {
    public static final String MODID = "whitelistr";
    private WClient webSocketClient;
    private Cache whitelistCache;

    static {
        try {
            FMLLog.info("-----Static initializer-----");
            FMLLog.info("[Whitelistr] Loading required libraries...");
            Libraries.loadLibraries();
            FMLLog.info("[Whitelistr] Libraries loaded successfully");
        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] FATAL ERROR: Failed to load required libraries!");
            e.printStackTrace();
            throw new RuntimeException("Failed to load Whitelistr dependencies", e);
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FMLLog.info("-----Pre-initializing-----");
        FMLLog.info("[Whitelistr] Loading configuration...");
        ConfigHandler.loadConfig();
        try {
            FMLLog.info("[Whitelistr] Initializing network components...");
            initializeNetworkComponents();
        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] FATAL ERROR: WebSocket initialization failed: %s", e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize WebSocket", e);
        }
    }

    @Mod.EventHandler
    public void postInit(FMLInitializationEvent event) {
        try {
            FMLLog.info("-----Post-initializing-----");
            FMLLog.info("[Whitelistr] Initializing cache system...");
            initializeCache();

            FMLLog.info("[Whitelistr] Registering event handlers...");
            registerEventHandlers();

        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] Initialization failed: %s", e.getMessage());
            throw new RuntimeException("Whitelistr initialization failed", e);
        }
    }

    private void initializeNetworkComponents() throws Exception {
        FMLLog.info("[Whitelistr] Connecting to WebSocket server...");
        webSocketClient = new WClient(
            ConfigHandler.WEBSOCKET_URL,
            ConfigHandler.SERVER_UUID,
            ConfigHandler.API_KEY
        );
        webSocketClient.connect();
    }

    private void initializeCache() {
        try {
            FMLLog.info("[Whitelistr] Initializing cache system...");
            whitelistCache = new Cache(new Database(), webSocketClient);
        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] Failed to initialize cache system: %s", e.getMessage());
            throw new RuntimeException("Failed to initialize cache system", e);
        }
    }

    private void registerEventHandlers() {
        PlayerEventHandler handler = new PlayerEventHandler(webSocketClient, whitelistCache);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(handler);
        FMLLog.info("[Whitelistr] Event handlers registered");
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        FMLLog.info("[Whitelistr] Server stopping - cleaning up resources...");
        if (webSocketClient != null) {
            FMLLog.info("[Whitelistr] Shutting down WebSocket client...");
            webSocketClient.shutdown();
        }
        if (whitelistCache != null) {
            FMLLog.info("[Whitelistr] Shutting down cache system...");
            whitelistCache.shutdown();
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        if (!MinecraftServer.getServer().isServerInOnlineMode()) {
            FMLLog.severe("[Whitelistr] Server is not in online mode. Disabling mod and shutting down server...");
            MinecraftServer.getServer().initiateShutdown();
        }

        FMLLog.info("[Whitelistr] Server started with UUID: %s", ConfigHandler.SERVER_UUID);
    }
}

