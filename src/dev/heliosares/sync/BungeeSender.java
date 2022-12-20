package dev.heliosares.sync;

import dev.heliosares.sync.bungee.SyncBungee;
import net.md_5.bungee.api.CommandSender;

public class BungeeSender implements MySender {

    private final SyncBungee plugin;
    private final CommandSender sender;

    public BungeeSender(SyncBungee plugin, CommandSender sender) {
        this.plugin = plugin;
        this.sender = sender;
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
        plugin.dispatchCommand(this, command);
    }

    @Override
    public String getName() {
        return sender.getName();
    }

    @Override
    public Object getSender() {
        return sender;
    }

}
