package dev.heliosares.sync.bungee;

import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.ServerClientHandler;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

public class MSyncCommand extends Command implements TabExecutor {
    private final SyncBungee plugin;

    public MSyncCommand(String name, SyncBungee plugin) {
        super(name);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sync.msync")) {
            SyncBungee.tell(sender, "§cNo permission.");
            return;
        }
        if (args.length == 0) {
            SyncBungee.tell(sender, "§cInvalid syntax.");
            return;
        }

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("-debug")) {
                plugin.debug = !plugin.debug;
                if (plugin.debug)
                    SyncBungee.tell(sender, "§aDebug enabled");
                else
                    SyncBungee.tell(sender, "§cDebug disabled");
                return;
            } else if (args[0].equalsIgnoreCase("-serverlist")) {
                String out = "Server statuses: ";
                List<ServerClientHandler> clients = plugin.sync.getClients();
                for (Entry<String, ServerInfo> entry : plugin.getProxy().getServers().entrySet()) {
                    ServerClientHandler ch = null;
                    for (ServerClientHandler other : clients) {
                        if (entry.getKey().equalsIgnoreCase(other.getName())) {
                            ch = other;
                            break;
                        }
                    }
                    boolean connected = ch != null && ch.isConnected();
                    out += "\n";
                    out += connected ? "§a" : "§c";
                    out += entry.getKey() + ": ";
                    out += connected ? "Online" : "Offline";
                }
                SyncBungee.tell(sender, out);
                return;
            } else if (args[0].equalsIgnoreCase("-playerlist")) {
                SyncBungee.tell(sender, plugin.sync.getUserManager().toFormattedString());
                return;
            }
        }

        String message = CommandParser.concat(0, args);
        Result serverR = CommandParser.parse("-s", message);
        if (plugin.sync.send(serverR.value(),
                new Packet(null, Packets.COMMAND.id, new JSONObject().put("command", serverR.remaining())))) {
            SyncBungee.tell(sender, "§aCommand sent.");
        } else {
            SyncBungee.tell(sender, "No servers found matching this name: " + serverR.value());
        }
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 0) {
            return out;
        }
        if (sender.hasPermission("sync.msync")) {
            if (args.length == 1) {
                out.add("-playerlist");
                out.add("-serverlist");
            }
            if (args.length > 1 && args[args.length - 2].equalsIgnoreCase("-s")) {
                plugin.sync.getClients().forEach(c -> out.add(c.getName()));
            } else {
                out.add("-s");
                out.add("-p");
            }
        }
        return CommandParser.tab(out, args[args.length - 1]);
    }
}
