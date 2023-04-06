package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.utils.EncryptionAES;
import dev.heliosares.sync.utils.EncryptionDH;
import dev.heliosares.sync.utils.EncryptionRSA;

import javax.annotation.Nullable;
import javax.crypto.SecretKey;
import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.AlgorithmParameters;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.List;
import java.util.function.Consumer;

public class SyncClient implements SyncNetCore {
    private final SyncCore plugin;
    private final NetEventHandler eventhandler;
    private final UserManager usermanager;
    private final EncryptionRSA encryptionRSA;
    private SocketConnection connection;
    private boolean closed;
    private int unableToConnectCount = 0;
    private List<String> servers;


    public SyncClient(SyncCore plugin, EncryptionRSA encryption) {
        this.plugin = plugin;
        this.eventhandler = new NetEventHandler(plugin);
        this.usermanager = new UserManager(plugin, this);
        this.encryptionRSA = encryption;
        eventhandler.registerListener(Packets.PLAYER_DATA.id, null, usermanager);
    }

    /**
     * Initiates the client. This should only be called once, onEnable
     *
     * @param port       Port of the proxy server
     * @param serverport Port of this Minecraft server
     */
    public void start(int port, int serverport) {
        if (connection != null) {
            throw new IllegalStateException("Client already started");
        }
        plugin.newThread(() -> {
            while (!closed) {
                if (unableToConnectCount < 3 || plugin.debug()) {
                    plugin.print("Client connecting on port " + port + "...");
                }
                try {
                    connection = new SocketConnection(new Socket(InetAddress.getLoopbackAddress(), port));

                    AlgorithmParameters params = EncryptionDH.generateParameters(connection.readRaw());
                    PublicKey serverPublicKey = EncryptionDH.getPublicKey(connection.readRaw());
                    KeyPair keyPair = EncryptionDH.generate(params);
                    SecretKey keyDB = EncryptionDH.combine(keyPair.getPrivate(), serverPublicKey);
                    connection.sendRaw(keyPair.getPublic().getEncoded());
                    connection.sendRaw(EncryptionDH.encrypt(keyDB, encryptionRSA.getUUID().toString().getBytes()));
                    connection.setEncryption(new EncryptionAES(encryptionRSA.decrypt(EncryptionDH.decrypt(keyDB, connection.readRaw()))));
                    connection.send("ACK".getBytes());
                    connection.setName(new String(connection.read()));

                    plugin.print("Authenticated as " + connection.getName());
                    unableToConnectCount = 0;

                    usermanager.sendPlayers("all");
                    usermanager.request("all");

                    while (!closed) { // Listen for packets
                        Packet packet = connection.listen();
                        if (packet == null) {
                            plugin.warning("Null packet received");
                            continue;
                        }
                        if (packet.getForward() != null) {
                            packet.setOrigin(packet.getForward());
                        } else {
                            packet.setOrigin("proxy");
                        }
                        if (packet.getPacketId() != Packets.KEEPALIVE.id) {
                            plugin.debug("received: " + packet);
                        }
                        if (packet.getPacketId() == Packets.SERVER_LIST.id) {
                            servers = packet.getPayload().getJSONArray("servers").toList().stream()
                                    .map(o -> (String) o).toList();
                        }

                        eventhandler.execute("proxy", packet);
                    }
                } catch (ConnectException e) {
                    if (!plugin.debug() && ++unableToConnectCount == 3) {
                        plugin.print("Server not available. Continuing to attempt silently...");
                    } else if (plugin.debug() || unableToConnectCount < 3) {
                        plugin.print("Server not available. Retrying...");
                    }
                } catch (NullPointerException | SocketException | EOFException e) {
                    plugin.print("Connection closed." + (closed ? "" : " Retrying..."));
                    if (plugin.debug()) {
                        plugin.print(e);
                    }
                    if (closed) {
                        return;
                    }
                } catch (Exception e) {
                    plugin.warning("Client crashed. Restarting...");
                    plugin.print(e);
                } finally {
                    closeTemporary();
                }
                try {
                    //noinspection BusyWait
                    Thread.sleep(unableToConnectCount > 3 ? 5000 : 1000);
                } catch (InterruptedException e) {
                    plugin.warning("Failed to delay");
                    plugin.print(e);
                }
            }
        });
    }

    /**
     * This should be called about once per second. Informs the server that it is
     * still connected. If no packet is received by the server for 10 seconds, the
     * client will be kicked.
     */
    public void keepalive() throws Exception {
        if (closed || connection == null || !connection.isConnected()) {
            return;
        }
        connection.sendKeepalive();
        if (System.currentTimeMillis() - connection.getTimeOfLastPacketReceived() > 10000) {
            closeTemporary();
            plugin.warning("timed out from proxy");
        }
    }

    /**
     * Permanently closes this instance of the client. Only call onDisable
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeTemporary();
    }

    /**
     * Will terminate the current connection to the server and cause the client to
     * attempt to reconnect.
     */
    public void closeTemporary() {
        if (connection == null) {
            return;
        }
        connection.close();
    }

    /**
     * Sends a packet!
     *
     * @return true if sent
     */
    public boolean send(Packet packet) throws IOException {
        checkAsync();
        connection.send(packet);
        return true;
    }

    public boolean send(@Nullable String server, Packet packet) throws IOException {
        checkAsync();
        if (server != null && !server.equals("all")) {
            if (servers == null || !servers.contains(server)) {
                return false;
            }
        }
        packet.setForward(server);
        send(packet);
        return true;
    }

    @Override
    public boolean sendConsumer(@Nullable String server, Packet packet, Consumer<Packet> responseConsumer) throws IOException {
        checkAsync();
        if (server != null) packet.setForward(server);
        connection.sendConsumer(packet, responseConsumer);
        return true;
    }

    private void checkAsync() {
        if (plugin.isAsync()) return;
        plugin.warning("Synchronous call to sync");
        if (plugin.debug()) Thread.dumpStack();
    }

    /**
     * @return The name of this server according to the proxy
     */
    public String getName() {
        if (connection == null) {
            return null;
        }
        return connection.getName();
    }

    /**
     * @return Whether the client is actively connected
     */
    public boolean isConnected() {
        if (connection == null) {
            return false;
        }
        return connection.isConnected();
    }

    public List<String> getServers() {
        return servers;
    }

    @Override
    public NetEventHandler getEventHandler() {
        return eventhandler;
    }

    public UserManager getUserManager() {
        return usermanager;
    }
}
