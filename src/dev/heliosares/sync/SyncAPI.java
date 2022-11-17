package dev.heliosares.sync;

import dev.heliosares.sync.bungee.SyncBungee;
import dev.heliosares.sync.net.NetListener;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.spigot.SyncSpigot;

import java.io.IOException;
import java.util.List;

public class SyncAPI {
    private static SyncCore instance;

    public static SyncCore getInstance() {
        if (instance != null) {
            return instance;
        }
        try {
            if ((instance = SyncSpigot.getInstance()) != null) {
                return instance;
            }
        } catch (Throwable ignored) {
        }
        try {
            if ((instance = SyncBungee.getInstance()) != null) {
                return instance;
            }
        } catch (Throwable ignored) {
        }
        return instance;
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

    /**
     * Unregisters all listeners on this channel
     *
     * @param channel
     */
    public static void unregister(String channel) {
        getInstance().getSync().getEventHandler().unregisterChannel(channel);
    }

    public static List<String> getServers() {
        return getInstance().getSync().getServers();
    }

}
