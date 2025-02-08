package eu.whitelistr;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.*;
import cpw.mods.fml.common.FMLLog;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.cache.Database;
import eu.whitelistr.commands.Commands;
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
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Loading required libraries...");
            Libraries.loadLibraries();
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Libraries loaded successfully");
        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] FATAL ERROR: Failed to load required libraries!");
            e.printStackTrace();
            throw new RuntimeException("Failed to load Whitelistr dependencies", e);
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FMLLog.info("-----Pre-initializing-----");
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Loading configuration...");
        ConfigHandler.loadConfig();
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Configuration loaded.");
        try {
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Initializing network components...");
            initializeNetworkComponents();
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Network components initialized.");
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
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Initializing cache system...");
            initializeCache();
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Cache system initialized.");

            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Registering event handlers...");
            registerEventHandlers();
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Event handlers registered.");

        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] Initialization failed: %s", e.getMessage());
            throw new RuntimeException("Whitelistr initialization failed", e);
        }
    }

    private void initializeNetworkComponents() throws Exception {
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Connecting to WebSocket server...");
        webSocketClient = new WClient(
            ConfigHandler.WEBSOCKET_URL,
            ConfigHandler.SERVER_UUID,
            ConfigHandler.API_KEY
        );
        webSocketClient.connect();
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] WebSocket client connected.");
    }

    private void initializeCache() {
        try {
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Initializing cache system... (Cache class)");
            whitelistCache = new Cache(new Database(), webSocketClient);
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Cache system (Cache class) initialized.");
        } catch (Exception e) {
            FMLLog.severe("[Whitelistr] Failed to initialize cache system (Cache class): %s", e.getMessage());
            throw new RuntimeException("Failed to initialize cache system", e);
        }
    }

    private void registerEventHandlers() {
        PlayerEventHandler handler = new PlayerEventHandler(webSocketClient, whitelistCache);
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(handler);
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Player event handler registered.");
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        FMLLog.info("[Whitelistr] Server stopping - cleaning up resources...");
        if (webSocketClient != null) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Shutting down WebSocket client...");
            try {
                webSocketClient.shutdown();
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] WebSocket client shutdown complete.");
            } catch (Exception e) {
                FMLLog.warning("[Whitelistr] Warning: Error shutting down WebSocket client: %s", e.getMessage());
            }
        }
        if (whitelistCache != null) {
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Shutting down cache system...");
            try {
                whitelistCache.shutdown();
                if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Cache system shutdown complete.");
            } catch (Exception e) {
                FMLLog.warning("[Whitelistr] Warning: Error shutting down cache system: %s", e.getMessage());
            }
        }
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Resource cleanup complete.");
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        Commands commands = new Commands(webSocketClient);
        try {
            commands.registerCommands(event);
            if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Commands registered.");
        } catch (Exception e) {
            FMLLog.warning("[Whitelistr] Warning: Error registering commands: %s", e.getMessage());
        }

        if (!MinecraftServer.getServer().isServerInOnlineMode()) {
            FMLLog.severe("[Whitelistr] Server is not in online mode. Disabling mod and shutting down server...");
            MinecraftServer.getServer().initiateShutdown();
        }
        if (ConfigHandler.DEBUG_MODE) FMLLog.info("[Whitelistr] Server online mode check passed.");

        FMLLog.info("[Whitelistr] Server started with UUID: %s", ConfigHandler.SERVER_UUID);
    }
}
