package dev.heliosares.sync;

import dev.heliosares.sync.bungee.SyncBungee;
import net.md_5.bungee.BungeeCord;
import net.md_5.bungee.api.CommandSender;

public class BungeeSender implements MySender {

	private final CommandSender sender;

	public BungeeSender(CommandSender sender) {
		this.sender = sender;
	}

	@Override
	public String getName() {
		return sender.getName();
	}

	@Override
	public void sendMessage(String msg) {
		SyncBungee.tell(sender, msg);
	}

	@Override
	public boolean hasPermission(String node) {
		return sender.hasPermission(node);
	}

	@Override
	public boolean hasPermissionExplicit(String node) {
		for (String perm : sender.getPermissions()) {
			if (perm.equalsIgnoreCase(node)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void execute(String command) {
		BungeeCord.getInstance().getPluginManager().dispatchCommand(sender, command);
	}

}
