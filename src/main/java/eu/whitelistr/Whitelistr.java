package eu.whitelistr;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import eu.whitelistr.cache.Cache;
import eu.whitelistr.cache.Database;
import eu.whitelistr.events.ConfigHandler;
import eu.whitelistr.events.PlayerEventHandler;
import eu.whitelistr.network.WClient;
import eu.whitelistr.utils.Libraries;

@Mod(modid = Whitelistr.MODID, version = "1.0", name = "Whitelistr", acceptableRemoteVersions = "*")
public class Whitelistr {

    public static final String MODID = "whitelistr";
    private WClient webSocketClient;
    private Cache whitelistCache;


    static {
        try {
            Libraries.loadLibraries();
        } catch(Exception e) {
            System.err.println("Failed to load external libraries: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ConfigHandler.loadConfig();
        try {
            webSocketClient = new WClient(ConfigHandler.WEBSOCKET_URL, ConfigHandler.SERVER_UUID, ConfigHandler.API_KEY);
            webSocketClient.connect();
            Database database = new Database();
            whitelistCache = new Cache(database, webSocketClient);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to initialize Whitelistr. Disabling mod.");
        }
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(
            new PlayerEventHandler(webSocketClient, whitelistCache)
        );

        System.out.println("Whitelistr Mod: Server Starting with UUID: " + ConfigHandler.SERVER_UUID);
    }
}
