package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCoreProxy;
import dev.heliosares.sync.utils.EncryptionAES;
import dev.heliosares.sync.utils.EncryptionRSA;
import org.json.JSONObject;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;

public class ServerClientHandler extends SocketConnection implements Runnable {

    private final SyncCoreProxy plugin;
    private final SyncServer server;
    private final EncryptionRSA encryptionRSA;

    public ServerClientHandler(SyncCoreProxy plugin, SyncServer server, Socket socket, EncryptionRSA encryptionRSA) throws IOException, InvalidKeyException {
        super(socket);
        this.plugin = plugin;
        this.server = server;
        this.encryptionRSA = encryptionRSA;
    }

    @Override
    public void run() {
        try {
            setEncryption(new EncryptionAES(encryptionRSA.decode(readRaw())));
        } catch (InvalidKeyException | IOException e) {
            close();
            server.remove(this);
            return;
        }
        while (isConnected()) {
            try {
                Packet packet = listen();
                if (packet == null) {
                    plugin.warning("Null packet received");
                    continue;
                }
                packet.setOrigin(getName());
                if (packet.getPacketId() != Packets.KEEPALIVE.id) {
                    plugin.debug("received from " + getName() + ": " + packet);
                }
                boolean noname = getName() == null;
                if (packet.getPacketId() == Packets.HANDSHAKE.id) {
                    if (!noname) {
                        plugin.warning("Client tried to handshake after connected. Disconnecting");
                        close();
                        return;
                    }
                    int port = packet.getPayload().getInt("serverport");
                    String name = port == -1 ? ("daemon" + (System.currentTimeMillis() % 1000000))
                            : plugin.getServerNameByPort(port);
                    setName(name);

                    plugin.print(name + " connected.");

                    send(new Packet(null, Packets.HANDSHAKE.id, new JSONObject().put("name", name)));

                    server.updateClientsWithServerList();
                } else if (noname) {
                    plugin.warning("Client tried to send packet without handshake. Disconnecting");
                    close();
                    return;
                } else if (packet.getPacketId() != Packets.KEEPALIVE.id) {
                    final String forward = packet.getForward();
                    if (packet.getForward() != null) {
                        packet.setForward(getName());
                        if (forward.equalsIgnoreCase("all")) {
                            server.getServers().forEach((c) -> {
                                if (!c.equals(getName())) {
                                    server.send(c, packet);
                                }
                            });
                        } else {
                            server.send(forward, packet);
                        }
                    }
                    if (forward == null || forward.equalsIgnoreCase("all")) {
                        server.getEventHandler().execute(getName(), packet);
                    }
                }
            } catch (NullPointerException | SocketException | EOFException e1) {
                if (plugin.debug()) {
                    plugin.print(e1);
                }
                break;
            } catch (Exception e) {
                plugin.print(e);
            }
        }
        if (getName() != null) {
            plugin.print(getName() + " disconnected.");
        }
        close();
        server.remove(this);
    }
}
