package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.net.packet.Packet;
import dev.kshl.kshlib.concurrent.ConcurrentCollection;
import dev.kshl.kshlib.encryption.EncryptionRSA;
import dev.kshl.kshlib.misc.Timer;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class SyncServer implements SyncNetCore {
    final SyncCoreProxy plugin;
    private final NetEventHandler eventhandler;
    private final ConcurrentCollection<ArrayList<ServerClientHandler>, ServerClientHandler> clients = new ConcurrentCollection<>(new ArrayList<>());
    private final UserManager usermanager;
    private Set<EncryptionRSA> clientEncryptionRSA;
    private ServerSocket serverSocket;
    private boolean closed = false;
    private final IDProvider idProvider = new IDProvider((short) 0);

    public SyncServer(SyncCoreProxy plugin) {
        this.plugin = plugin;
        this.eventhandler = new NetEventHandler(plugin);
        this.usermanager = new UserManager(plugin, this);
        eventhandler.registerListener(PacketType.PLAYER_DATA, null, usermanager);
    }

    /**
     * Send to all servers
     */
    public boolean send(Packet packet) {
        return send(null, packet);
    }

    /**
     * Same as send(Packet) but for a specific server/servers
     *
     * @param server The name of the server (separated by commas for multiple), or
     *               null or "all" for all servers.
     */
    public boolean send(@Nullable String server, Packet packet) {
        return send(server, packet, null);
    }

    @Override
    public boolean send(@Nullable String server, Packet packet, @Nullable Consumer<Packet> responseConsumer) {
        return send(server, packet, responseConsumer, 0, null);
    }

    @Override
    public boolean send(@Nullable String server, Packet packet, @Nullable Consumer<Packet> responseConsumer, long timeoutMillis, @Nullable Runnable timeoutAction) {
        packet.assignResponseID(idProvider);
        return clients.function(clients -> {
            boolean any = false;
            String[] servers = (server == null || server.equals("all")) ? null : server.split(",");
            Iterator<ServerClientHandler> it = clients.iterator();
            while (it.hasNext()) {
                ServerClientHandler ch = it.next();
                if (ch.getName() == null || !ch.isConnected()) {
                    continue;
                }
                contains:
                // Checks that the server is included in the list, or skips if the list is null
                if (servers != null) {
                    for (String other : servers) {
                        if (other.equalsIgnoreCase(ch.getName())) {
                            break contains;
                        }
                    }
                    continue;
                }
                try {
                    ch.send(packet, responseConsumer, timeoutMillis, timeoutAction);
                    any = true;
                } catch (IOException e) {
                    plugin.warning("Error while sending to: " + ch.getName() + ". Kicking");
                    plugin.print(e);
                    ch.close();
                    it.remove();
                }
            }
            return any;
        });
    }

    @Override
    @Deprecated
    public boolean sendConsumer(@Nullable String server, Packet packet, @Nullable Consumer<Packet> responseConsumer) {
        return send(server, packet, responseConsumer);
    }

    /**
     * Initializes the server. This should only be called once onEnable
     *
     * @param port The port to listen to
     */
    public void start(String host, int port) {
        if (serverSocket != null) {
            throw new IllegalStateException("Server already started");
        }
        plugin.newThread(() -> {
            // This loop restarts the server on failure
            while (!closed) {
                try {
                    serverSocket = new ServerSocket(port, 0, InetAddress.getByName(host));

                    plugin.print("Server running on port " + port + ".");
                    // This look waits for clients
                    while (!closed) {
                        Socket socket = serverSocket.accept();
                        ServerClientHandler ch = new ServerClientHandler(plugin, SyncServer.this, socket);

                        plugin.debug("Connection accepted from port " + socket.getPort());

                        clients.add(ch);
                        plugin.newThread(ch);
                    }
                } catch (SocketException e1) {
                    plugin.print("Server closed.");
                    return;
                } catch (Exception e1) {
                    plugin.warning("Server crashed:");
                    plugin.print(e1);
                } finally {
                    closeTemporary();
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(5000);
                    // Not busy waiting. A delay to wait to restart
                } catch (InterruptedException e) {
                    plugin.warning("Failed to delay");
                    plugin.print(e);
                }
            }
        });

        plugin.scheduleAsync(this::keepAlive, 100, 1000);
    }

    /**
     * Checks for timed out clients. This should be called about once per second.
     * Clients will be timed out after 10 seconds of not sending any packets.
     * <p>
     * Sends a keepAlive packet to all clients
     */
    public void keepAlive() {
        clients.consume(clients -> {
            Iterator<ServerClientHandler> it = clients.iterator();
            while (it.hasNext()) {
                ServerClientHandler ch = it.next();
                boolean remove = false;
                if (ch.getName() == null) {
                    if (ch.getAge() > 3000) {
                        remove = true;
                    } else {
                        continue;// Still connecting
                    }
                } else if (!ch.isConnected()) {
                    remove = true;
                } else if (System.currentTimeMillis() - ch.getTimeOfLastPacketReceived() > 10000) {
                    plugin.print(ch.getName() + " timed out");
                    remove = true;
                } else {
                    try {
                        ch.sendKeepAlive();
                    } catch (IOException e) {
                        plugin.print(ch.getName() + " timed out");
                        remove = true;
                    }
                }
                if (remove) {
                    ch.close();
                    it.remove();
                }
            }
        });
    }

    public void updateClientsWithServerList() {
        send(new Packet(null, PacketType.SERVER_LIST, new JSONObject().put("servers", new JSONArray(getServers()))));
    }

    /**
     * Permanently closes this server. Call only onDisable
     */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeTemporary();
    }

    /**
     * Temporarily closes this server. This will cause the server to restart.
     */
    @Override
    public void closeTemporary() {
        clients.forEach(ServerClientHandler::close);
        if (serverSocket == null || serverSocket.isClosed()) {
            return;
        }
        try {
            serverSocket.close();
        } catch (IOException e) {
            plugin.print(e);
        }
    }

    /**
     * Closes then removes the specified SocketConnection (Client)
     *
     * @param ch Connection to remove
     */
    public void remove(ServerClientHandler ch) {
        ch.close();
        clients.remove(ch);
        updateClientsWithServerList();
    }

    /**
     * @return an unmodifiableList of all clients currently connected
     */
    public List<ServerClientHandler> getClients() {
        return clients.function(clients -> clients.stream().toList());
    }

    /**
     * @return a set of all actively connected servers
     */
    @Override
    public Set<String> getServers() {
        Set<String> out = new HashSet<>();
        clients.forEach((c) -> {
            if (c != null && c.isConnected() && c.getName() != null) {
                out.add(c.getName());
            }
        });
        return out;
    }

    @Override
    public NetEventHandler getEventHandler() {
        return eventhandler;
    }

    @Override
    public String getName() {
        return "proxy";
    }

    public UserManager getUserManager() {
        return usermanager;
    }

    EncryptionRSA getEncryptionFor(UUID user) {
        return clientEncryptionRSA.stream()
                .filter(entry -> entry.getUUID().equals(user))
                .findAny().orElse(null);
    }

    public void setClientEncryptionRSA(Set<EncryptionRSA> clientEncryptionRSA) {
        this.clientEncryptionRSA = clientEncryptionRSA;
    }

    public boolean hasWritePermission(String user) {
        return plugin.hasWritePermission(user);
    }

}
