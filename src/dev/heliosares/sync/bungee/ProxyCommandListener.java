package dev.heliosares.sync.bungee;

import java.util.List;
import java.util.Map.Entry;

import org.json.JSONObject;

import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.ServerClientHandler;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.plugin.Command;

public class ProxyCommandListener extends Command {
	private final SyncBungee plugin;

	public ProxyCommandListener(String name, SyncBungee plugin) {
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
			} else if (args[0].equalsIgnoreCase("-list")) {
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
			}
		}

		String message = CommandParser.concat(args);
		Result serverR = CommandParser.parse("-s", message);
		if (plugin.sync.send(serverR.value(),
				new Packet(null, Packets.COMMAND.id, new JSONObject().put("command", serverR.remaining())))) {
			SyncBungee.tell(sender, "§aCommand sent.");
		} else {
			SyncBungee.tell(sender, "No servers found matching this name: " + serverR.value());
		}
	}
}
