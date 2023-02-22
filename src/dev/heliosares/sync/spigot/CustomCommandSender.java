package dev.heliosares.sync.spigot;

import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class CustomCommandSender implements ConsoleCommandSender {
    private final Consumer<String> output;
    private final ConsoleCommandSender handle;

    public CustomCommandSender(Consumer<String> output) {
        this.handle = Bukkit.getConsoleSender();
        this.output = output;
    }

    @Override
    public void sendMessage(@NotNull String s) {
        output.accept(s);
        handle.sendMessage(s);
    }

    @Override
    public void sendMessage(@NotNull String... strings) {
        for (String s : strings) {
            output.accept(s);
        }
        handle.sendMessage(strings);
    }

    @Override
    public void sendMessage(@Nullable UUID uuid, @NotNull String s) {
        sendMessage(s);
    }

    @Override
    public void sendMessage(@Nullable UUID uuid, @NotNull String... strings) {
        sendMessage(strings);
    }

    @Override
    public boolean isPermissionSet(@NotNull String s) {
        return handle.isPermissionSet(s);
    }

    @Override
    public boolean isPermissionSet(@NotNull Permission permission) {
        return handle.isPermissionSet(permission);
    }

    @Override
    public boolean hasPermission(@NotNull String s) {
        return handle.hasPermission(s);
    }

    @Override
    public boolean hasPermission(@NotNull Permission permission) {
        return handle.hasPermission(permission);
    }

    @NotNull
    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String s, boolean b) {
        return handle.addAttachment(plugin, s, b);
    }

    @NotNull
    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin) {
        return handle.addAttachment(plugin);
    }

    @Nullable
    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin, @NotNull String s, boolean b, int i) {
        return handle.addAttachment(plugin, s, b, i);
    }

    @Nullable
    @Override
    public PermissionAttachment addAttachment(@NotNull Plugin plugin, int i) {
        return handle.addAttachment(plugin, i);
    }

    @Override
    public void removeAttachment(@NotNull PermissionAttachment permissionAttachment) {
        handle.removeAttachment(permissionAttachment);
    }

    @Override
    public void recalculatePermissions() {
        handle.recalculatePermissions();
    }

    @NotNull
    @Override
    public Set<PermissionAttachmentInfo> getEffectivePermissions() {
        return handle.getEffectivePermissions();
    }

    @NotNull
    @Override
    public Server getServer() {
        return handle.getServer();
    }

    @NotNull
    @Override
    public String getName() {
        return handle.getName();
    }

    @NotNull
    @Override
    public Spigot spigot() {
        return new CommandSender.Spigot() {
            public void sendMessage(@NotNull BaseComponent component) {
                output.accept(BaseComponent.toPlainText(component));
                handle.spigot().sendMessage(component);
            }

            public void sendMessage(@NotNull BaseComponent... components) {
                output.accept(BaseComponent.toPlainText(components));
                handle.spigot().sendMessage(components);
            }

            public void sendMessage(@Nullable UUID sender, @NotNull BaseComponent component) {
                sendMessage(component);
            }

            public void sendMessage(@Nullable UUID sender, @NotNull BaseComponent... components) {
                sendMessage(components);
            }
        };
    }

    @Override
    public boolean isConversing() {
        return handle.isConversing();
    }

    @Override
    public void acceptConversationInput(@NotNull String s) {
        handle.acceptConversationInput(s);
    }

    @Override
    public boolean beginConversation(@NotNull Conversation conversation) {
        return handle.beginConversation(conversation);
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation) {
        handle.abandonConversation(conversation);
    }

    @Override
    public void abandonConversation(@NotNull Conversation conversation, @NotNull ConversationAbandonedEvent conversationAbandonedEvent) {
        handle.abandonConversation(conversation, conversationAbandonedEvent);
    }

    @Override
    public void sendRawMessage(@NotNull String s) {
        output.accept(s);
        handle.sendRawMessage(s);
    }

    @Override
    public void sendRawMessage(@Nullable UUID uuid, @NotNull String s) {
        output.accept(s);
        handle.sendMessage(s);
    }

    @Override
    public boolean isOp() {
        return handle.isOp();
    }

    @Override
    public void setOp(boolean b) {
    }
}
