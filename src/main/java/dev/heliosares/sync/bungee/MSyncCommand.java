package dev.heliosares.sync.bungee;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.ServerClientHandler;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

        if (args[0].startsWith("-")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("-debug")) {
                    plugin.debug = !plugin.debug;
                    if (plugin.debug)
                        SyncBungee.tell(sender, "§aDebug enabled");
                    else
                        SyncBungee.tell(sender, "§cDebug disabled");
                    return;
                } else if (args[0].equalsIgnoreCase("-serverlist")) {
                    StringBuilder out = new StringBuilder("Server statuses: ");
                    List<ServerClientHandler> clients = plugin.getSync().getClients();
                    Set<String> servers = new HashSet<>(plugin.getProxy().getServers().keySet());
                    servers.addAll(plugin.getSync().getServers());
                    for (String server : servers) {
                        ServerClientHandler ch = null;
                        for (ServerClientHandler other : clients) {
                            if (server.equalsIgnoreCase(other.getName())) {
                                ch = other;
                                break;
                            }
                        }
                        boolean connected = ch != null && ch.isConnected();
                        out.append("\n");
                        out.append(connected ? "§a" : "§c");
                        out.append(server).append(": ");
                        out.append(connected ? "Online" : "Offline");
                    }
                    SyncBungee.tell(sender, out.toString());
                    return;
                } else if (args[0].equalsIgnoreCase("-playerlist")) {
                    ComponentBuilder builder = new ComponentBuilder();
                    plugin.getSync().getUserManager().makeFormattedString(builder::append, s -> builder.event(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(s))));
                    sender.sendMessage(builder.create());
                    return;
                } else if (args[0].equalsIgnoreCase("-reloadkeys") && sender.equals(plugin.getProxy().getConsole())) {
                    plugin.reloadKeys(true);
                    return;
                }
            } else if (args[0].equalsIgnoreCase("-set") || args[0].equalsIgnoreCase("-get")) {
                boolean set = args[0].equalsIgnoreCase("-set");
                if (args.length != (set ? 4 : 3)) {
                    SyncBungee.tell(sender, "§cInvalid syntax.");
                    return;
                }
                ProxiedPlayer target;
                try {
                    target = plugin.getProxy().getPlayer(UUID.fromString(args[1]));
                } catch (IllegalArgumentException ignored) {
                    target = plugin.getProxy().getPlayer(args[1]);
                }
                PlayerData data;
                if (target == null || (data = plugin.getSync().getUserManager().getPlayer(target.getUniqueId())) == null) {
                    SyncBungee.tell(sender, "§cPlayer not found.");
                    return;
                }

                String value;
                if (set) {
                    data.getCustomString("Sync", args[2], true).set(value = args[3]);
                } else {
                    value = data.getCustomString("Sync", args[2], true).get();
                }
                SyncBungee.tell(target, target.getName() + " - " + args[2] + "=" + value);
                return;
            }
        }

        String message = CommandParser.concat(0, args);
        Result serverR = CommandParser.parse("-s", message);
        message = serverR.remaining();
        Packet packet = new Packet(null, PacketType.COMMAND, new JSONObject().put("command", message));

        if (plugin.getSync().send(serverR.value(), packet, response -> SyncBungee.tell(sender, response.getPayload().getString("msg")))) {
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
                if (sender.equals(plugin.getProxy().getConsole())) out.add("-reloadkeys");
            }
            if (args.length > 1 && args[args.length - 2].equalsIgnoreCase("-s")) {
                plugin.getSync().getClients().forEach(c -> out.add(c.getName()));
            } else {
                out.add("-s");
                out.add("-p");
            }
        }
        return CommandParser.tab(out, args[args.length - 1]);
    }
}
