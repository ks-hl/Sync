package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import dev.heliosares.sync.utils.EncryptionAES;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SocketConnection {
    private EncryptionAES encryption;
    private final Socket socket;
    private final DataOutputStream out;
    private final DataInputStream in;
    private final long created;
    private final Map<Long, ResponseAction> responses = new HashMap<>();
    private boolean closed;
    private String name;
    private long lastPacketSent;
    private long lastPacketReceived;
    private long lastCleanup;

    public SocketConnection(Socket socket) throws IOException, InvalidKeyException {
        this.socket = socket;
        this.created = System.currentTimeMillis();
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }

    protected EncryptionAES getEncryption() {
        return encryption;
    }

    protected void setEncryption(EncryptionAES encryption) throws InvalidKeyException {
        if (this.encryption != null)
            throw new IllegalStateException("Attempted to re set the encryption key. Key can only be set once per SocketConnection");
        encryption.encrypt(new byte[1]); // Validates the key
        this.encryption = encryption;
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

    public Packet listen() throws Exception {
        try {
            synchronized (in) {
                Packet packet = new Packet(new JSONObject(new String(read())));
                if (packet.getPacketId() != Packets.KEEPALIVE.id)
                    SyncAPI.getInstance().debug("RECV: " + packet);
                if (packet.getPacketId() == Packets.BLOB.id) packet.setBlob(read());
                if (packet.isResponse()) {
                    ResponseAction action = responses.get(packet.getResponseID());
                    try {
                        if (action != null) action.action().accept(packet);
                    } catch (Throwable t) {
                        SyncAPI.getInstance().warning("Error while handling response packet " + packet);
                        SyncAPI.getInstance().print(t);
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

    public void sendKeepalive() throws IOException {
        if (System.currentTimeMillis() - getTimeOfLastPacketSent() < 750) {
            return;
        }
        if (getName() == null) {
            return;
        }
        send(new Packet(null, Packets.KEEPALIVE.id, null));
    }

    protected void send(Packet packet) throws IOException {
        sendConsumer(packet, null);
    }

    protected void sendConsumer(Packet packet, @Nullable Consumer<Packet> responseConsumer) throws IOException {
        if (closed) {
            return;
        }
        if (getName() == null && packet.getPacketId() != Packets.HANDSHAKE.id) {
            throw new IllegalStateException("Cannot send packets before handshake.");
        }
        if (packet.isResponse() && responseConsumer != null)
            throw new IllegalArgumentException("Cannot specify consumer for a response");
        synchronized (out) {
            if (responseConsumer != null)
                responses.put(packet.getResponseID(), new ResponseAction(System.currentTimeMillis(), responseConsumer));
            String plain = packet.toString();
            if (packet.getPacketId() != Packets.KEEPALIVE.id) SyncAPI.getInstance().debug("SEND: " + plain);
            send(plain.getBytes());
            if (packet.getPacketId() == Packets.BLOB.id) send(packet.getBlob());
            out.flush();
        }
        this.lastPacketSent = System.currentTimeMillis();
    }

    protected void send(byte[] b) throws IOException {
        try {
            b = encryption.encrypt(b);
        } catch (InvalidKeyException e) {
            // Unexpected, as this is validated on creation
            throw new RuntimeException(e);
        }
        sendRaw(b);
    }

    protected void sendRaw(byte[] b) throws IOException {
        synchronized (out) {
            for (int i = 0; i < 8; i++)
                out.write(i);
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
        for (int i = 0; i < 8; i++) {
            int next;
            while ((next = in.read()) != i)
                if (next == -1) {
                    throw new SocketException("Server/client desync occurred. Disconnected.");
                }
        }
        synchronized (in) {
            int size = in.readInt();
            if (size == 0) return new byte[0];
            if (size > 1000000000) throw new IOException("Packet size too large (" + size + ">1,000,000,000");

            return in.readNBytes(size);
        }
    }

    private void cleanup() {
        if (System.currentTimeMillis() - lastCleanup < 60000) return;
        lastCleanup = System.currentTimeMillis();
        responses.entrySet().removeIf(a -> System.currentTimeMillis() - a.getValue().created > 15000L);
    }

    private record ResponseAction(long created, @Nonnull Consumer<Packet> action) {
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
}
