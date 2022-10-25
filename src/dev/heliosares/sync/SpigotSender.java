package dev.heliosares.sync;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class SpigotSender implements MySender {

	private final CommandSender sender;

	public SpigotSender(CommandSender sender) {
		this.sender = sender;
	}

	@Override
	public String getName() {
		return sender.getName();
	}

	@Override
	public void sendMessage(String msg) {
		sender.sendMessage(msg);
	}

	@Override
	public boolean hasPermission(String node) {
		return sender.hasPermission(node);
	}

	@Override
	public boolean hasPermissionExplicit(String node) {
		for (PermissionAttachmentInfo perm : sender.getEffectivePermissions()) {
			if (perm.getPermission().equalsIgnoreCase(node)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void execute(String command) {
		Bukkit.dispatchCommand(sender, command);
	}

	@Override
	public Object getSender() {
		return sender;
	}
}
