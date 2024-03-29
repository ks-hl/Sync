package dev.heliosares.sync;

import dev.heliosares.sync.net.NetEventHandler;
import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.net.packet.MessagePacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.ShowTitlePacket;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

public class SyncAPI {
    private static SyncCore instance;
    public static UUID ConsoleUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    public static final String PROTOCOL_VERSION = "3";

    public static @Nonnull SyncCore getInstance() {
        if (instance != null) return instance;
        throw new IllegalStateException("No instance of Sync");
    }

    public static void setInstance(SyncCore instance) {
        if (SyncAPI.instance != null) throw new IllegalStateException("Can not re-set instance");
        SyncAPI.instance = instance;
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

    @Deprecated
    public static boolean send(dev.heliosares.sync.net.Packet packet) throws Exception {
        SyncAPI.getInstance().print("Deprecated API usage");
        Thread.dumpStack();
        return send((Packet) packet);
    }

    @Deprecated
    public static boolean send(String server, dev.heliosares.sync.net.Packet packet) throws Exception {
        SyncAPI.getInstance().print("Deprecated API usage");
        Thread.dumpStack();
        return send(server, (Packet) packet);
    }

    @Deprecated
    public static void register(int packetID, String channel, NetEventHandler.PacketConsumer consumer) {
        SyncAPI.getInstance().print("Deprecated API usage");
        Thread.dumpStack();
        getInstance().getSync().getEventHandler().registerListener(packetID, channel, consumer);
    }

    public static void register(PacketType type, String channel, NetEventHandler.PacketConsumer consumer) {
        getInstance().getSync().getEventHandler().registerListener(type, channel, consumer);
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
    @Deprecated
    public static void sendMessage(@Nullable String to, String raw, @Nullable String node) throws Exception {
        broadcastMessage(new JSONObject().put("msg", raw), to, node);
    }

    @Deprecated
    private static void broadcastMessage(JSONObject payload, @Nullable String to, @Nullable String node) throws Exception {
        SyncAPI.getInstance().print("Deprecated API usage");
        Thread.dumpStack();
        if (getInstance().getSync() instanceof SyncServer)
            throw new UnsupportedOperationException("Messages can only be sent from clients");
        if (node != null) payload.put("node", node);
        if (to != null) payload.put("to", to);
        send(new Packet(null, PacketType.MESSAGE, payload));
    }

    /**
     * Shows a title screen to the target player
     *
     * @param to The UUID of the player. If null, all players on the network will be shown the title screen
     */
    @Deprecated
    public static void sendTitle(@Nullable UUID to, @Nullable String title, @Nullable String subtitle, int fadein, int duration, int fadeout) throws Exception {
        getInstance().warning("Deprecated API usage");
        Thread.dumpStack();
        String server = "all";
        if (to != null) server = getPlayer(to).getServer();
        send(server, new ShowTitlePacket(title, subtitle, fadein, duration, fadeout, to, null));
    }

    /**
     * Broadcasts a message to all players consume the node
     */
    public static void sendMessage(String msg, @Nullable String node) throws Exception {
        sendMessage(msg, node, false);
    }

    /**
     * Broadcasts a message to all players consume the node
     */
    public static void sendMessage(String msg, @Nullable String node, boolean othersOnly) throws Exception {
        send(new MessagePacket(node, null, null, msg, null, othersOnly));
    }


    /**
     * Shows a title screen to the target player
     */
    public static void broadcastTitle(@Nullable String title, @Nullable String subtitle, int fadein, int duration, int fadeout) throws Exception {
        send("all", new ShowTitlePacket(title, subtitle, fadein, duration, fadeout, null, null));
    }

    /**
     * Executes a runnable using the respective platform's scheduler
     */
    public static void runAsync(Runnable runnable) {
        getInstance().runAsync(runnable);
    }
}
