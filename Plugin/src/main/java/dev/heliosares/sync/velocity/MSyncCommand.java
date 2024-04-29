package dev.heliosares.sync.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.ServerClientHandler;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class MSyncCommand implements SimpleCommand {
    private final SyncVelocity plugin;

    public MSyncCommand(SyncVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        String[] args = invocation.arguments();
        CommandSource sender = invocation.source();
        if (!sender.hasPermission("sync.msync")) {
            SyncVelocity.tell(sender, "§cNo permission.");
            return;
        }
        if (args.length == 0) {
            SyncVelocity.tell(sender, "§cInvalid syntax.");
            return;
        }

        if (args[0].startsWith("-")) {
            if (args.length == 1) {
                if (args[0].equalsIgnoreCase("-debug")) {
                    plugin.debug = !plugin.debug;
                    if (plugin.debug)
                        SyncVelocity.tell(sender, "§aDebug enabled");
                    else
                        SyncVelocity.tell(sender, "§cDebug disabled");
                    return;
                } else if (args[0].equalsIgnoreCase("-serverlist")) {
                    StringBuilder out = new StringBuilder("Server statuses: ");
                    List<ServerClientHandler> clients = plugin.getSync().getClients();
                    Set<String> servers = plugin.getProxy().getAllServers().stream().map(RegisteredServer::getServerInfo).map(ServerInfo::getName).collect(Collectors.toSet());
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
                    SyncVelocity.tell(sender, out.toString());
                    return;
                } else if (args[0].equalsIgnoreCase("-playerlist")) {

                    TextComponent.Builder builder = Component.text();
                    plugin.getSync().getUserManager().makeFormattedString(
                            line -> builder.append(Component.text(line)),
                            (line, hover) -> builder.append(Component.text(line).hoverEvent(HoverEvent.showText(Component.text(hover)))));
                    sender.sendMessage(builder);
                    return;
                } else if (args[0].equalsIgnoreCase("-reloadkeys") && sender.equals(plugin.getProxy().getConsoleCommandSource())) {
                    plugin.reloadKeys(true);
                    return;
                }
            } else if (args[0].equalsIgnoreCase("-set") || args[0].equalsIgnoreCase("-get")) {
                boolean set = args[0].equalsIgnoreCase("-set");
                if (args.length != (set ? 4 : 3)) {
                    SyncVelocity.tell(sender, "§cInvalid syntax.");
                    return;
                }
                Optional<Player> target;
                try {
                    target = plugin.getProxy().getPlayer(UUID.fromString(args[1]));
                } catch (IllegalArgumentException ignored) {
                    target = plugin.getProxy().getPlayer(args[1]);
                }
                PlayerData data;
                if (target.isEmpty() || (data = plugin.getSync().getUserManager().getPlayer(target.get().getUniqueId())) == null) {
                    SyncVelocity.tell(sender, "§cPlayer not found.");
                    return;
                }

                String value;
                if (set) {
                    data.getCustomString("Sync", args[2], true).set(value = args[3]);
                } else {
                    value = data.getCustomString("Sync", args[2], true).get();
                }
                SyncVelocity.tell(sender, target.get().getUsername() + " - " + args[2] + "=" + value);
                return;
            }
        }

        String message = CommandParser.concat(0, args);
        Result serverR = CommandParser.parse("-s", message);
        message = serverR.remaining();
        Packet packet = new Packet(null, PacketType.COMMAND, new JSONObject().put("command", message));

        if (plugin.getSync().send(serverR.value(), packet, response -> SyncVelocity.tell(sender, response.getPayload().getString("msg")))) {
            SyncVelocity.tell(sender, "§aCommand sent.");
        } else {
            SyncVelocity.tell(sender, "No servers found matching this name: " + serverR.value());
        }
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        List<String> out = new ArrayList<>();
        String[] args = invocation.arguments();
        CommandSource sender = invocation.source();
        if (args.length == 0) {
            return out;
        }
        if (sender.hasPermission("sync.msync")) {
            if (args.length == 1) {
                out.add("-playerlist");
                out.add("-serverlist");
                if (sender.equals(plugin.getProxy().getConsoleCommandSource())) out.add("-reloadkeys");
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

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("sync.msync");
    }
}
