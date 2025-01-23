package eu.whitelistr;

import eu.whitelistr.events.ConfigHandler;
import eu.whitelistr.events.PlayerEventHandler;
import eu.whitelistr.network.WClient;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public class Whitelistr extends JavaPlugin {

    private WClient webSocketClient;

    @Override
    public void onEnable() {
        ConfigHandler.loadConfig();
        try {
            webSocketClient = new WClient(ConfigHandler.WEBSOCKET_URL, ConfigHandler.SERVER_UUID, ConfigHandler.API_KEY);
            webSocketClient.connect();
        } catch (Exception e) {
            e.printStackTrace();
            getLogger().severe("WebSocket connection failed. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(new PlayerEventHandler(webSocketClient), this);
        getLogger().info("Whitelistr plugin enabled with UUID: " + ConfigHandler.SERVER_UUID);
    }

    @Override
    public void onDisable() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
        }
        HandlerList.unregisterAll(this);

        getLogger().info("Whitelistr plugin disabled.");
    }
}



