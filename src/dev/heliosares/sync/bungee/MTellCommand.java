package dev.heliosares.sync.bungee;

import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

public class MTellCommand extends Command {
    private final SyncBungee plugin;

    public MTellCommand(String name, SyncBungee plugin) {
        super(name);
        this.plugin = plugin;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("sync.mtell")) {
            SyncBungee.tell(sender, "§cNo permission.");
            return;
        }
        if (args.length < 2) {
            SyncBungee.tell(sender, "§cInvalid syntax");
            return;
        }
        ProxiedPlayer target = plugin.getProxy().getPlayer(args[0]);
        if (target == null) {
            SyncBungee.tell(sender, "§cPlayer not found");
            return;
        }
        StringBuilder msg = new StringBuilder(args[1]);
        for (int i = 2; i < args.length; i++) msg.append(" ").append(args[i]);
        SyncBungee.tell(target, msg.toString());
    }
}
