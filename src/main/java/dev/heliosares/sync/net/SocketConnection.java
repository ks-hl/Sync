package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.utils.EncryptionAES;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Manages a Socket's connection. Agnostic to client/server.
 */
public class SocketConnection {
    private EncryptionAES encryption;
    private final SyncCore plugin;
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;
    private final long created;
    private final Map<Long, ResponseAction> responses = new HashMap<>();
    private boolean closed;
    private String name;
    private long lastPacketSent = System.currentTimeMillis();
    private long lastPacketReceived = System.currentTimeMillis();
    private long lastCleanup;

    public SocketConnection(SyncCore plugin, Socket socket) throws IOException {
        this.plugin = plugin;
        this.socket = socket;
        this.created = System.currentTimeMillis();
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    protected EncryptionAES getEncryption() {
        return encryption;
    }


    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            socket.close();
        } catch (Throwable ignored) {
        }
    }

    @Nonnull
    public Packet listen() throws Exception {
        try {
            synchronized (in) {
                Packet packet = PacketType.getPacketFromJSON(new JSONObject(new String(read())));
                if (packet.getType() != PacketType.KEEP_ALIVE) plugin.debug(() -> {
                    String action = "RECV";
                    if (this instanceof ServerClientHandler sch) {
                        if (packet.getForward() != null) action = "FWD";
                        action += " (" + sch.getName();
                        if (packet.getForward() != null) action += " -> " + packet.getForward();
                        action += ")";
                    }
                    return action + ": " + packet.toJSON().toString(2);
                });
                if (packet instanceof BlobPacket blobPacket) blobPacket.setBlob(read());
                if (packet.isResponse()) {
                    ResponseAction action = responses.get(packet.getResponseID());
                    try {
                        if (action != null) action.action().accept(packet);
                    } catch (Throwable t) {
                        plugin.warning("Error while handling response packet " + packet);
                        plugin.print(t);
                    }
                }
                this.lastPacketReceived = System.currentTimeMillis();
                cleanup();
                return packet;
            }
        } catch (NullPointerException e) {
            throw new IOException("null packet received");
        }
    }

    public void sendKeepAlive() throws IOException {
        if (System.currentTimeMillis() - getTimeOfLastPacketSent() < 500) return;

        if (getName() == null) return;

        send(new Packet(null, PacketType.KEEP_ALIVE, null), null);
    }

    protected void send(Packet packet, @Nullable Consumer<Packet> responseConsumer) throws IOException {
        if (closed) return;
        if (packet.isResponse() && responseConsumer != null)
            throw new IllegalArgumentException("Cannot specify consumer for a response");
        synchronized (out) {
            if (responseConsumer != null)
                responses.put(packet.getResponseID(), new ResponseAction(System.currentTimeMillis(), responseConsumer));
            String plain = packet.toString();
            if (packet.getType() != PacketType.KEEP_ALIVE && (packet.getForward() == null || (!(this instanceof ServerClientHandler)))) // Don't debug for forwarding packets, that was already accomplished on receipt
                plugin.debug(() -> "SEND" + ((this instanceof ServerClientHandler sch) ? (" (" + sch.getName() + ")") : "") + ": " + packet.toJSON().toString(2));
            send(plain.getBytes());
            if (packet instanceof BlobPacket blobPacket) send(blobPacket.getBlob());
            out.flush();
        }
        this.lastPacketSent = System.currentTimeMillis();
    }

    protected void send(byte[] b) throws IOException {
        try {
            b = encryption.encrypt(b);
        } catch (InvalidKeyException e) {
            throw new IOException("Invalid session key. This is unexpected..");
        }
        sendRaw(b);
    }

    protected void sendRaw(byte[] b) throws IOException {
        synchronized (out) {
            out.writeInt(b == null ? 0 : b.length);
            if (b != null && b.length > 0) {
                out.write(b);
            }
        }
    }

    protected byte[] read() throws IOException, InvalidKeyException {
        return encryption.decrypt(readRaw());
    }

    protected byte[] readRaw() throws IOException {
        synchronized (in) {
            int size = in.readInt();
            if (size == 0) return new byte[0];
            if (size > 100000000) throw new IOException("Packet size too large (" + size + ">100,000,000");
            if (size < 0) throw new IOException("Packet size < 0");

            return in.readNBytes(size);
        }
    }

    private void cleanup() {
        if (System.currentTimeMillis() - lastCleanup < 60000) return;
        lastCleanup = System.currentTimeMillis();
        responses.values().removeIf(ResponseAction::shouldRemove);
    }

    private record ResponseAction(long created, @Nonnull Consumer<Packet> action) {
        boolean shouldRemove() {
            return System.currentTimeMillis() - created > 300000L;
        }
    }

    public long getAge() {
        return System.currentTimeMillis() - created;
    }

    public String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    public boolean isConnected() {
        if (closed) {
            return false;
        }
        if (socket == null || socket.isClosed()) {
            return false;
        }
        return socket.isConnected();
    }

    public long getTimeOfLastPacketSent() {
        return lastPacketSent;
    }

    public long getTimeOfLastPacketReceived() {
        return lastPacketReceived;
    }

    public String getIP() {
        return socket.getRemoteSocketAddress().toString();
    }

    public void setEncryption(EncryptionAES encryption) {
        if (this.encryption == null) this.encryption = encryption;
    }

    public boolean isClosed() {
        return closed;
    }
}
