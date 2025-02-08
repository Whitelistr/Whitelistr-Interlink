package eu.whitelistr.commands;

import com.google.gson.JsonObject;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import eu.whitelistr.network.WClient;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

public class Commands {

    private final WClient wClient;

    public Commands(WClient wClient) {
        this.wClient = wClient;
    }

    public void registerCommands(FMLServerStartingEvent event) {
        event.registerServerCommand(new CommandBase() {
            @Override
            public String getCommandName() {
                return "whitelistr";
            }

            @Override
            public String getCommandUsage(ICommandSender sender) {
                return "/whitelistr refresh";
            }

            @Override
            public void processCommand(ICommandSender sender, String[] args) {
                if (args.length > 0 && "refresh".equalsIgnoreCase(args[0])) {
                    JsonObject request = new JsonObject();
                    request.addProperty("action", "sendCache");
                    wClient.send(request.toString());
                    sender.addChatMessage(new ChatComponentText("Whitelist cache refresh requested."));
                } else {
                    sender.addChatMessage(new ChatComponentText("Usage: /whitelistr refresh"));
                }
            }

            @Override
            public int getRequiredPermissionLevel() {
                return 2;
            }
        });
    }
}
