package dev.heliosares.sync.spigot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;

public class SpigotConsoleSender implements ConsoleCommandSender {

	private ArrayList<String> output = new ArrayList<>();

	public List<String> getOutput() {
		return Collections.unmodifiableList(output);
	}

	@Override
	public void sendMessage(String message) {
		Bukkit.getPluginManager().getPlugin("Sync").getLogger().info(getName() + ": " + message);
		output.add(message);
	}

	@Override
	public void sendMessage(String... messages) {
		for (String message : messages) {
			sendMessage(message);
		}
	}

	@Override
	public void sendMessage(UUID sender, String message) {
		sendMessage(message);
	}

	@Override
	public void sendMessage(UUID sender, String... messages) {
		sendMessage(messages);
	}

	@Override
	public void sendRawMessage(String message) {
		sendMessage(message);
	}

	@Override
	public void sendRawMessage(UUID sender, String message) {
		sendMessage(message);
	}

	@Override
	public Server getServer() {
		return Bukkit.getServer();
	}

	@Override
	public String getName() {
		return "SyncConsole";
	}

	@Override
	public Spigot spigot() {
		return Bukkit.getConsoleSender().spigot();
	}

	@Override
	public boolean isPermissionSet(String name) {
		return true;
	}

	@Override
	public boolean isPermissionSet(Permission perm) {
		return true;
	}

	@Override
	public boolean hasPermission(String name) {
		return true;
	}

	@Override
	public boolean hasPermission(Permission perm) {
		return true;
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
		return null;
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin) {
		return null;
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
		return null;
	}

	@Override
	public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
		return null;
	}

	@Override
	public void removeAttachment(PermissionAttachment attachment) {
	}

	@Override
	public void recalculatePermissions() {
	}

	@Override
	public Set<PermissionAttachmentInfo> getEffectivePermissions() {
		return null;
	}

	@Override
	public boolean isOp() {
		return true;
	}

	@Override
	public void setOp(boolean value) {
	}

	@Override
	public boolean isConversing() {
		return false;
	}

	@Override
	public void acceptConversationInput(String input) {
	}

	@Override
	public boolean beginConversation(Conversation conversation) {
		return false;
	}

	@Override
	public void abandonConversation(Conversation conversation) {
	}

	@Override
	public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
	}

}
