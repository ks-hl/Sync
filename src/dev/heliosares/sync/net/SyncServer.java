package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCoreProxy;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SyncServer implements SyncNetCore {
    final SyncCoreProxy plugin;
    private final NetEventHandler eventhandler;
    private ServerSocket serverSocket;
    private ArrayList<ServerClientHandler> clients = new ArrayList<>();
    private UserManager usermanager;
    private boolean closed = false;

    public SyncServer(SyncCoreProxy plugin) {
        this.plugin = plugin;
        this.eventhandler = new NetEventHandler(plugin);
        this.usermanager = new UserManager(plugin, this);
        eventhandler.registerListener(usermanager);
    }

    /**
     * Send to all servers
     *
     * @param packetid
     * @param packet
     */
    public boolean send(Packet packet) {
        return send(null, packet);
    }

    /**
     * Same as send(Packet) but for a specific server/servers
     *
     * @param server The name of the server (separated by commas for multiple), or
     *               null or "all" for all servers.
     * @param packet
     * @return
     */
    public boolean send(String server, Packet packet) {
        boolean any = false;
        synchronized (clients) {
            String servers[] = (server == null || server.equals("all")) ? null : server.split(",");
            Iterator<ServerClientHandler> it = clients.iterator();
            outer:
            while (it.hasNext()) {
                ServerClientHandler ch = it.next();
                if (ch.getName() == null || !ch.isConnected()) {
                    continue;
                }
                contains:
                if (servers != null) {
                    for (String other : servers) {
                        if (other.equalsIgnoreCase(ch.getName())) {
                            break contains;
                        }
                    }
                    continue outer;
                }
                try {
                    ch.send(packet);
                    any = true;
                } catch (IOException | GeneralSecurityException e) {
                    plugin.warning("Error while sending to: " + ch.getName() + ". Kicking");
                    plugin.print(e);
                    ch.close();
                    it.remove();
                }
            }
        }
        return any;
    }

    /**
     * Initializes the server. This should only be called once onEnable
     *
     * @param port The port to listen to
     * @throws IOException
     */
    public void start(int port) throws IOException {
        if (serverSocket != null) {
            throw new IllegalStateException("Server already started");
        }
        plugin.runAsync(new Runnable() {
            @Override
            public void run() {
                // This loop restarts the server on failure
                while (!closed) {
                    try {
                        serverSocket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress());

                        plugin.print("Server running on port " + port + ".");
                        // This look waits for clients
                        while (!closed) {
                            Socket socket = serverSocket.accept();
                            ServerClientHandler ch = new ServerClientHandler(plugin, SyncServer.this, socket);

                            plugin.debug("Connection accepted on port " + socket.getPort());

                            synchronized (clients) {
                                clients.add(ch);
                            }
                            plugin.runAsync(ch);
                        }
                    } catch (SocketException e1) {
                        plugin.print("Server closed.");
                        return;
                    } catch (IOException e1) {
                        plugin.warning("Server crashed:");
                        plugin.print(e1);
                    } finally {
                        closeTemporary();
                    }
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        plugin.warning("Failed to delay");
                        plugin.print(e);
                    }
                }
            }
        });
    }

    /**
     * Checks for timed out clients. This should be called about once per second.
     * Clients will be timed out after 10 seconds of not sending any packets.
     * <p>
     * Sends a keepalive packet to all clients
     */
    public void keepalive() {
        synchronized (clients) {
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
                        ch.sendKeepalive();
                    } catch (GeneralSecurityException | IOException e) {
                        plugin.print(ch.getName() + " timed out");
                        remove = true;
                    }
                }
                if (remove) {
                    ch.close();
                    it.remove();
                }
            }
        }
    }

    public void updateClientsWithServerList() throws IOException {
        send(new Packet(null, Packets.SERVER_LIST.id, new JSONObject().put("servers", new JSONArray(getServers()))));
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
        synchronized (clients) {
            for (SocketConnection ch : this.clients) {
                ch.close();
            }
        }
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
    public void remove(SocketConnection ch) {
        ch.close();
        synchronized (clients) {
            clients.remove(ch);
        }
        try {
            updateClientsWithServerList();
        } catch (IOException ignored) {
        }
    }

    /**
     * @return an unmodifiableList of all clients currently connected
     */
    public List<ServerClientHandler> getClients() {
        synchronized (clients) {
            return Collections.unmodifiableList(clients);
        }
    }

    /**
     * @return a list of all actively connected servers
     */
    @Override
    public List<String> getServers() {
        List<String> out = new ArrayList<>();
        synchronized (clients) {
            clients.forEach((c) -> {
                if (c != null && c.isConnected() && c.getName() != null) {
                    out.add(c.getName());
                }
            });
        }
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
}
