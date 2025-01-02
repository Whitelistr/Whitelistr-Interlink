package eu.whitelistr.events;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import eu.whitelistr.network.WClient;
import eu.whitelistr.data.PlayerInfo;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;

import java.net.InetSocketAddress;

public class PlayerEventHandler {

    private final WClient webSocketClient;


    public PlayerEventHandler(WClient webSocketClient) {
        this.webSocketClient = webSocketClient;
    }

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            NetworkManager networkManager = ((EntityPlayerMP) event.player).playerNetServerHandler.netManager;
            InetSocketAddress remoteAddress = (InetSocketAddress) networkManager.getSocketAddress();
            String playerIP = remoteAddress.getAddress().getHostAddress();

            PlayerInfo playerInfo = new PlayerInfo(
                playerIP,
                event.player.getDisplayName(),
                event.player.getUniqueID().toString(),
                remoteAddress.getHostName(),
                System.currentTimeMillis(),
                ConfigHandler.SERVER_UUID
            );
            if (!webSocketClient.isPlayerWhitelisted(playerInfo.getUsername())) {
                ((EntityPlayerMP) event.player).playerNetServerHandler.kickPlayerFromServer("You are not whitelisted!");
            } else {
                System.out.println("Player " + playerInfo.getUsername() + " joined successfully.");
            }
        }
    }
}

