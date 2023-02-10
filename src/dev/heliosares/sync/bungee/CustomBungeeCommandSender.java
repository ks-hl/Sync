package dev.heliosares.sync.bungee;

import dev.heliosares.sync.SyncAPI;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;

import java.util.Collection;
import java.util.function.Consumer;

public class CustomBungeeCommandSender implements CommandSender {
    private final Consumer<String> output;
    private final CommandSender handle;

    public CustomBungeeCommandSender(Consumer<String> output) {
        this.handle = ((SyncBungee) SyncAPI.getInstance()).getProxy().getConsole();
        this.output = output;
    }

    @Override
    public String getName() {
        return handle.getName();
    }

    @Override
    public void sendMessage(String s) {
        output.accept(s);
    }

    @Override
    public void sendMessages(String... strings) {
        for (String s : strings) output.accept(s);
    }

    @Override
    public void sendMessage(BaseComponent... components) {
        output.accept(BaseComponent.toPlainText(components));
    }

    @Override
    public void sendMessage(BaseComponent component) {
        output.accept(BaseComponent.toPlainText(component));
    }

    @Override
    public Collection<String> getGroups() {
        return handle.getGroups();
    }

    @Override
    public void addGroups(String... strings) {
        handle.addGroups(strings);
    }

    @Override
    public void removeGroups(String... strings) {
        handle.removeGroups(strings);
    }

    @Override
    public boolean hasPermission(String s) {
        return handle.hasPermission(s);
    }

    @Override
    public void setPermission(String s, boolean b) {
    }

    @Override
    public Collection<String> getPermissions() {
        return handle.getPermissions();
    }
}
