package eu.whitelistr;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import eu.whitelistr.events.ConfigHandler;
import eu.whitelistr.events.PlayerEventHandler;
import eu.whitelistr.network.WClient;


@Mod(modid = Whitelistr.MODID, version = "1.0", name = "Whitelistr", acceptableRemoteVersions = "*")
public class Whitelistr {

    public static final String MODID = "whitelistr";
    private WClient webSocketClient;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHandler.loadConfig();
        try {
            webSocketClient = new WClient("wss://api.whitelistr.space/interlink", ConfigHandler.SERVER_UUID, ConfigHandler.API_KEY);
            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
            if (ConfigHandler.FAIL_ON_DISCONNECT) {
                throw new RuntimeException("WebSocket connection failed and failOnDisconnect is enabled.");
            }
        }
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new PlayerEventHandler(webSocketClient));
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        System.out.println("Whitelistr Mod: Server Starting with UUID: " + ConfigHandler.SERVER_UUID);
    }
}
