package dev.heliosares.sync.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;

public class MTellCommand implements SimpleCommand {
    private final SyncVelocity plugin;

    public MTellCommand(SyncVelocity plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource sender = invocation.source();
        if (!sender.hasPermission("sync.mtell")) {
            SyncVelocity.tell(sender, "§cNo permission.");
            return;
        }
        String[] args = invocation.arguments();
        if (args.length < 2) {
            SyncVelocity.tell(sender, "§cInvalid syntax");
            return;
        }
        plugin.getProxy().getPlayer(args[0]).ifPresentOrElse(target -> {
            StringBuilder msg = new StringBuilder(args[1]);
            for (int i = 2; i < args.length; i++) msg.append(" ").append(args[i]);
            SyncVelocity.tell(target, msg.toString());
        }, () -> SyncVelocity.tell(sender, "§cPlayer not found"));
    }
}
