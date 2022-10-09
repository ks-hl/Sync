package dev.heliosares.sync.utils;

import dev.heliosares.sync.MySender;
import dev.heliosares.sync.SyncCore;

public class CommandParser {
	public static void handle(SyncCore plugin, MySender sender, String command, String[] args) {
//		if (args == null) {
//			int firstspace = command.indexOf(" ");
//			if (firstspace > 0) {
//				command = command.substring(0, firstspace);
//				args = command.substring(firstspace + 1, command.length()).split(" ");
//			} else {
//				args = new String[0];
//			}
//		}
		if (sender != null) {
			if (!sender.hasPermissionExplicit("sync." + command)) {
				sender.sendMessage("§cNo permission");
				return;
			}
		}
	}

	public static void handleIncoming(SyncCore plugin, String command) {
		Result playerR = CommandParser.parse("-p", command);
		MySender sender = null;
		if (playerR.value() != null) {
			sender = plugin.getSender(playerR.value());
			command = playerR.remaining();
			if (sender == null) {
				plugin.print("Player not found: " + playerR.value());
				return;
			}
		}
		plugin.dispatchCommand(sender, command);
	}

	public static record Result(String remaining, String value) {
	};

	public static Result parse(String key, String cmd) {
		String args[] = cmd.split(" ");
		String value = null;
		String out = "";
		boolean escape = false;
		int i = 0;
		for (; i < args.length; i++) {
			if (i > 0 && (args[i].equalsIgnoreCase("psync") || args[i].equalsIgnoreCase("msync"))) {
				escape = true; // Prevents parsing out parts of the command which are parts of a sub-command
			}
			if (!escape && value == null && args[i].equalsIgnoreCase(key) && i < args.length - 1) {
				value = args[++i];
				continue;
			}
			out += args[i];
			if (i < args.length - 1) {
				out += " ";
			}
		}

		return new Result(out, value);
	}

	public static String concat(String... args) {
		String out = "";
		for (int i = 0; i < args.length; i++) {
			if (out.length() > 0) {
				out += " ";
			}
			out += args[i];
		}
		return out;
	}
}
