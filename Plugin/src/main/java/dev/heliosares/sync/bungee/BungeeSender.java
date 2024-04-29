package dev.heliosares.sync.bungee;

import dev.heliosares.sync.MySender;
import net.md_5.bungee.api.CommandSender;

public class BungeeSender extends MySender<CommandSender, SyncBungee> {

    public BungeeSender(SyncBungee plugin, CommandSender sender) {
        super(sender, plugin);
    }

    @Override
    public void sendMessage(String msg) {
        SyncBungee.tell(getSender(), msg);
    }

    @Override
    public boolean hasPermission(String node) {
        return getSender().hasPermission(node);
    }

    @Override
    public void execute(String command) {
        getPlugin().dispatchCommand(this, command);
    }

    @Override
    public String getName() {
        return getSender().getName();
    }
}
