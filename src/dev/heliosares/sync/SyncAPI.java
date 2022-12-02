package dev.heliosares.sync;

import dev.heliosares.sync.bungee.SyncBungee;
import dev.heliosares.sync.daemon.SyncDaemon;
import dev.heliosares.sync.net.NetListener;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.spigot.SyncSpigot;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

public class SyncAPI {
    private static SyncCore instance;

    public static @Nonnull SyncCore getInstance() {
        if (instance != null) {
            return instance;
        }
        try {
            if ((instance = SyncSpigot.getInstance()) != null) return instance;
        } catch (Throwable ignored) {
        }
        try {
            if ((instance = SyncBungee.getInstance()) != null) return instance;
        } catch (Throwable ignored) {
        }
        try {
            if ((instance = SyncDaemon.getInstance()) != null) return instance;
        } catch (Throwable ignored) {
        }
        throw new IllegalStateException("No instance of Sync");
    }

    /**
     * Sends a packet to a specific server.
     *
     * @param server The server to target. Must be contained within
     *               SyncAPI.getServers()
     * @param packet
     */
    public static boolean send(String server, Packet packet) throws Exception {
        return getInstance().getSync().send(server, packet);
    }

    /**
     * Sends a packet to the other endpoint. If executed from a server, goes to the
     * proxy. If executed from the proxy, goes to all servers.
     *
     * @param packet
     * @throws IOException
     */
    public static boolean send(Packet packet) throws Exception {
        return getInstance().getSync().send(packet);
    }

    public static void register(NetListener listen) {
        getInstance().getSync().getEventHandler().registerListener(listen);
    }

    public static void unregister(NetListener listen) {
        getInstance().getSync().getEventHandler().unregisterListener(listen);
    }

    public static PlayerData getPlayer(String name) {
        return getInstance().getSync().getUserManager().getPlayer(name);
    }

    public static PlayerData getPlayer(UUID uuid) {
        return getInstance().getSync().getUserManager().getPlayer(uuid);
    }

    /**
     * Unregisters all listeners on this channel
     *
     */
    public static void unregister(String channel) {
        getInstance().getSync().getEventHandler().unregisterChannel(channel);
    }

    public static List<String> getServers() {
        return getInstance().getSync().getServers();
    }

}
