package dev.heliosares.sync.spigot;

import dev.heliosares.sync.MySender;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.permissions.PermissionAttachmentInfo;

public class SpigotSender extends MySender<CommandSender, SyncSpigot> {

    public SpigotSender(CommandSender sender, SyncSpigot plugin) {
        super(sender, plugin);
    }

    @Override
    public void sendMessage(String msg) {
        getSender().sendMessage(msg);
    }

    @Override
    public boolean hasPermission(String node) {
        return getSender().hasPermission(node);
    }

    @Override
    public void execute(String command) {
        Bukkit.dispatchCommand(getSender(), command);
    }

    @Override
    public String getName() {
        return getSender().getName();
    }
}
