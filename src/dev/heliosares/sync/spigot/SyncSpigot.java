package dev.heliosares.sync.spigot;

import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.json.JSONObject;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.NetListener;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.utils.CommandParser;
import dev.heliosares.sync.utils.CommandParser.Result;
import dev.heliosares.sync.utils.FormulaParser;

public class SyncSpigot extends JavaPlugin implements CommandExecutor, SyncCore {
	private SyncClient sync;
	private boolean debug;

	@Override
	public void onEnable() {
		this.getConfig().options().copyDefaults(true);
		this.saveDefaultConfig();

		this.getCommand("psync").setExecutor(this);

		sync = new SyncClient(this);
		try {
			sync.start("127.0.0.1", getConfig().getInt("port", 8001), this.getServer().getPort());
		} catch (IOException e1) {
			warning("Error while enabling.");
			print(e1);
			this.setEnabled(false);
			return;
		}

		sync.registerListener(new NetListener(Packets.COMMAND.id, null) {
			@Override
			public void execute(String server, Packet packet) {
				try {
					String message = packet.getPayload().getString("command");

					print("Executing: " + message);

					Result playerR = CommandParser.parse("-p", message);
					CommandSender sender = null;
					if (playerR.value() == null) {
						sender = getServer().getConsoleSender();
					} else {
						sender = getServer().getPlayer(playerR.value());
						message = playerR.remaining();
						if (sender == null) {
							print("Player not found: " + playerR.value());
							return;
						}
					}
					dispatchCommand(sender, message);
				} catch (Exception e) {
					getLogger().warning("Error while parsing: ");
					print(e);
				}
			}
		});

		new BukkitRunnable() {

			@Override
			public void run() {
				if (!sync.isConnected()) {
					return;
				}
				try {
					sync.keepalive();
				} catch (Exception e) {
					warning("Error while sending keepalive:");
					print(e);
				}
			}
		}.runTaskTimerAsynchronously(this, 20, 20);
	}

	private void dispatchCommand(CommandSender sender, String command) {
		new BukkitRunnable() {
			@Override
			public void run() {
				getServer().dispatchCommand(sender, command);
			}
		}.runTask(this);
	}

	@Override
	public void onDisable() {
		sync.close();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		if (cmd.getLabel().equalsIgnoreCase("psync")) {
			if (!sender.hasPermission("sync.psync")) {
				sender.sendMessage("§cNo permission");
				return true;
			}
			if (args.length == 0) {
				sender.sendMessage("§cInvalid syntax");
				return true;
			}

			if (args.length == 1 && args[0].equalsIgnoreCase("-debug")) {
				debug = !debug;
				if (debug)
					sender.sendMessage("§aDebug enabled");
				else
					sender.sendMessage("§cDebug disabled");
				return true;
			}

			try {
				sync.send(new Packet(null, Packets.COMMAND.id,
						new JSONObject().put("command", CommandParser.concat(args))));
			} catch (Exception e) {
				sender.sendMessage("§cAn error occured");
				print(e);
				return true;
			}
			sender.sendMessage("§aCommand sent.");
			return true;
		} else if (cmd.getLabel().equalsIgnoreCase("if")) {
			if (!sender.hasPermission("sync.if")) {
				sender.sendMessage("§cNo permission");
				return true;
			}
			String condition = "";
			String commandIf = "";
			String commandElse = "";
			boolean then = false;
			boolean el = false;
			for (int i = 0; i < args.length; i++) {
				String part = args[i];
				if (part.equalsIgnoreCase("then")) {
					if (condition.length() == 0) {
						sender.sendMessage("§cNo condition provided");
						return true;
					}
					then = true;
					continue;
				}
				if (then) {
					if (part.equalsIgnoreCase("else")) {
						el = true;
						continue;
					}
					if (el) {
						commandElse += part;
					} else {
						commandIf += part;
					}
				} else {
					condition += part;
				}
			}
			if (!then) {
				sender.sendMessage("§cNo 'then' provided.");
				return true;
			}
			if (commandIf.length() == 0) {
				sender.sendMessage("§cNo command provided.");
				return true;
			}
			FormulaParser parser = new FormulaParser(condition);

			String command;
			boolean state;
			try {
				state = parser.solve() == 1;
			} catch (RuntimeException e) {
				sender.sendMessage("§c" + e.getMessage());
				return true;
			}
			if (state) {
				command = commandIf;
			} else {
				command = commandElse;
			}
			if (command.length() > 0) {
				Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
			}
			return true;
		}
		return false;
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
	public void warning(String msg) {
		getLogger().warning(msg);
	}

	@Override
	public boolean debug() {
		return debug;
	}
}
