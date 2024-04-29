package dev.heliosares.sync;

public abstract class MySender<S, P extends SyncCore> {
    private final S sender;
    private final P plugin;

    public MySender(S sender, P plugin) {
        this.sender = sender;
        this.plugin = plugin;
    }

    public abstract void sendMessage(String msg);

    public abstract boolean hasPermission(String node);

    public abstract void execute(String command);

    public abstract String getName();

    public final S getSender() {
        return sender;
    }

    public final P getPlugin() {
        return plugin;
    }
}
