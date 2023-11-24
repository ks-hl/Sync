package dev.heliosares.sync;

import dev.heliosares.sync.bungee.SyncBungee;
import dev.heliosares.sync.daemon.SyncDaemon;
import dev.heliosares.sync.net.*;
import dev.heliosares.sync.spigot.SyncSpigot;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SyncAPI {
    private static SyncCore instance;
    public static UUID ConsoleUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    public static @Nonnull SyncCore getInstance() {
        if (instance != null) {
            return instance;
        }
        try {
            if ((instance = SyncSpigot.getInstance()) != null) return instance;
        } catch (NoClassDefFoundError ignored) {
        }
        try {
            if ((instance = SyncBungee.getInstance()) != null) return instance;
        } catch (NoClassDefFoundError ignored) {
        }
        try {
            if ((instance = SyncDaemon.getInstance()) != null) return instance;
        } catch (NoClassDefFoundError ignored) {
        }
        throw new IllegalStateException("No instance of Sync");
    }

    /**
     * Sends a packet to a specific server.
     *
     * @param server The server to target. Must be contained within
     *               SyncAPI.getServers() or "all"
     */
    public static boolean send(String server, Packet packet) throws Exception {
        return getInstance().getSync().send(server, packet);
    }

    /**
     * Sends a packet to the other endpoint. If executed from a server, goes to the
     * proxy. If executed from the proxy, goes to all servers.
     */
    public static boolean send(Packet packet) throws Exception {
        return getInstance().getSync().send(packet);
    }

    public static void register(int packetID, String channel, NetEventHandler.PacketConsumer consumer) {
        getInstance().getSync().getEventHandler().registerListener(packetID, channel, consumer);
    }

    public static PlayerData getPlayer(String name) {
        return getInstance().getSync().getUserManager().getPlayer(name);
    }

    public static PlayerData getPlayer(UUID uuid) {
        return getInstance().getSync().getUserManager().getPlayer(uuid);
    }

    /**
     * Unregisters all listeners on this channel
     */
    public static void unregister(String channel) {
        getInstance().getSync().getEventHandler().unregisterChannel(channel);
    }

    public static Set<String> getServers() {
        return getInstance().getSync().getServers();
    }

    /**
     * Broadcasts a message to all players consume the node
     */
    public static void sendMessage(@Nullable String to, BaseComponent[] msg, @Nullable String node) throws Exception {
        sendMessage(to, msg, node, false);
    }

    /**
     * Broadcasts a message to all players consume the node
     *
     * @param othersOnly if true, will not display to players on the sending server
     */
    public static void sendMessage(@Nullable String to, BaseComponent[] msg, @Nullable String node, boolean othersOnly) throws Exception {
        JSONObject packet = new JSONObject();
        packet.put("json", ComponentSerializer.toString(msg).replace("[JSON]", ""));
        if (othersOnly) packet.put("others_only", true);
        broadcastMessage(packet, to, node);
    }

    /**
     * Broadcasts a message to all players consume the node
     */
    public static void sendMessage(@Nullable String to, String raw, @Nullable String node) throws Exception {
        broadcastMessage(new JSONObject().put("msg", raw), to, node);
    }

    private static void broadcastMessage(JSONObject payload, @Nullable String to, @Nullable String node) throws Exception {
        if (getInstance().getSync() instanceof SyncServer)
            throw new UnsupportedOperationException("Messages can only be sent from clients");
        if (node != null) payload.put("node", node);
        if (to != null) payload.put("to", to);
        send(new Packet(null, Packets.MESSAGE.id, payload));
    }

    /**
     * Shows a title screen to the target player
     *
     * @param to The UUID of the player. If null, all players on the network will be shown the title screen
     */
    public static void sendTitle(@Nullable UUID to, @Nullable String title, @Nullable String subtitle, int fadein, int duration, int fadeout) throws Exception {
        JSONObject payload = new JSONObject();

        if (to != null) payload.put("to", to);
        if (title != null) payload.put("title", title);
        if (subtitle != null) payload.put("subtitle", subtitle);
        if (fadein > 0) payload.put("fadein", fadein);
        if (duration > 0) payload.put("duration", duration);
        if (fadeout > 0) payload.put("fadeout", fadeout);

        String server = "all";
        if (to != null) server = getPlayer(to).getServer();
        send(server, new Packet(null, Packets.TITLE.id, payload));
    }

    /**
     * Executes a runnable using the respective platform's scheduler
     */
    public static void runAsync(Runnable runnable) {
        getInstance().runAsync(runnable);
    }
}
