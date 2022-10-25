package dev.heliosares.sync.bungee;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import org.json.JSONObject;

import dev.heliosares.sync.BungeeSender;
import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.NetListener;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class SyncBungee extends Plugin implements SyncCoreProxy {
	protected Configuration config;
	protected Configuration data;
	SyncServer sync;
	boolean debug;

	@Override
	public void onEnable() {
		print("Enabling");
		getProxy().getPluginManager().registerCommand(this, new ProxyCommandListener("msync", this));

		sync = new SyncServer(this);
		loadConfig();
		try {
			sync.start(config.getInt("port", 8001));
		} catch (IOException e1) {
			warning("Error while enabling.");
			print(e1);
			this.onDisable();
			return;
		}

		sync.registerListener(new NetListener(Packets.COMMAND.id, null) {
			@Override
			public void execute(String server, Packet packet) {
				try {
					String message = packet.getPayload().getString("command");

					print("Executing: " + message);

					Result serverR = CommandParser.parse("-s", message);
					if (serverR.value() != null) {
						if (!sync.send(serverR.value(), new Packet(null, packet.getPacketId(),
								new JSONObject().put("command", serverR.remaining())))) {
							warning("No servers found matching this name: " + serverR.value());
						}
						return;
					}

					Result playerR = CommandParser.parse("-p", message);

					CommandSender sender = null;
					if (playerR.value() == null) {
						sender = getProxy().getConsole();
					} else {
						sender = getProxy().getPlayer(playerR.value());
						if (sender == null) {
							print("Player not found");
							return;
						}
					}
					debug("out: " + playerR.remaining());
					getProxy().getPluginManager().dispatchCommand(sender, playerR.remaining());
				} catch (Exception e) {
					getLogger().warning("Error while parsing: ");
					print(e);
				}
			}
		});

		getProxy().getScheduler().schedule(this, new Runnable() {
			@Override
			public void run() {
				sync.keepalive();
			}
		}, 1, 1, TimeUnit.SECONDS);
	}

	@Override
	public void onDisable() {
		print("Closing");
		sync.close();
	}

	public void loadConfig() {
		if (!getDataFolder().exists())
			getDataFolder().mkdir();

		File file = new File(getDataFolder(), "config.yml");

		if (!file.exists()) {
			try (InputStream in = getResourceAsStream("config.yml")) {
				Files.copy(in, file.toPath());
			} catch (IOException e) {
				print(e);
			}
		}
		try {
			config = ConfigurationProvider.getProvider(YamlConfiguration.class)
					.load(new File(getDataFolder(), "config.yml"));
		} catch (IOException e) {
			print(e);
		}
	}

	public static void tell(CommandSender sender, String msg) {
		sender.sendMessage(TextComponent.fromLegacyText(ChatColor.translateAlternateColorCodes('&', msg)));
	}

	public void print(String msg) {
		getLogger().info(msg);
	}

	public void print(Throwable t) {
		getLogger().log(Level.WARNING, t.getMessage(), t);
	}

	public void debug(String msg) {
		if (debug) {
			print(msg);
		}
	}

	@Override
	public void runAsync(Runnable run) {
		new Thread(run).start();
	}

	@Override
	public String getServerNameByPort(int port) {
		for (Entry<String, ServerInfo> info : getProxy().getServers().entrySet()) {
			if (info.getValue().getSocketAddress() instanceof InetSocketAddress) {
				if (port == ((InetSocketAddress) info.getValue().getSocketAddress()).getPort()) {
					return info.getKey();
				}
			}
		}
		return null;
	}

	@Override
	public void warning(String msg) {
		getLogger().warning(msg);
	}

	@Override
	public boolean debug() {
		return debug;
	}

	@Override
	public MySender getSender(String name) {
		ProxiedPlayer player = getProxy().getPlayer(name);
		return player == null ? null : new BungeeSender(player);
	}

	@Override
	public void dispatchCommand(MySender sender, String command) {
		sender.execute(command);
	}
}
