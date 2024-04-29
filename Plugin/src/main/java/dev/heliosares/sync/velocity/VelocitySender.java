package dev.heliosares.sync.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import dev.heliosares.sync.MySender;

public class VelocitySender extends MySender<CommandSource, SyncVelocity> {

    public VelocitySender(SyncVelocity plugin, CommandSource sender) {
        super(sender, plugin);
    }

    @Override
    public void sendMessage(String msg) {
        SyncVelocity.tell(getSender(), msg);
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
        if (getSender() instanceof Player player) return player.getUsername();
        if (getSender() instanceof ConsoleCommandSource consoleCommandSource) return "#console";
        return null;
    }
}
