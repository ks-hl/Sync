package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class SocketConnection {
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

    public SocketConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.created = System.currentTimeMillis();
        out = new DataOutputStream(socket.getOutputStream());
        in = new DataInputStream(socket.getInputStream());
    }

    public long getAge() {
        return System.currentTimeMillis() - created;
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

    private byte[] read() throws IOException {
        for (int i = 0; i < 8; i++) {
            int next;
            while ((next = in.read()) != i)
                if (next == -1) {
                    throw new SocketException("disconnected");
                }
        }
        synchronized (in) {
            int size = in.readInt();
            if (size == 0) {
                return new byte[0];
            }
            return in.readNBytes(size);
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
                    if (action != null) action.action().accept(packet);
                }
                this.lastPacketReceived = System.currentTimeMillis();
                cleanup();
                return packet;
            }
        } catch (NullPointerException e) {
            throw new IOException("null packet received");
        }
    }

    private void cleanup() {
        if (System.currentTimeMillis() - lastCleanup < 60000) return;
        lastCleanup = System.currentTimeMillis();
        responses.entrySet().removeIf(a -> System.currentTimeMillis() - a.getValue().created > 15000L);
    }

    private void send(byte[] b) throws IOException {
        synchronized (out) {
            for (int i = 0; i < 8; i++)
                out.write(i);
            out.writeInt(b == null ? 0 : b.length);
            if (b != null && b.length > 0) {
                out.write(b);
            }
        }
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

    public long getTimeOfLastPacketSent() {
        return lastPacketSent;
    }

    public long getTimeOfLastPacketReceived() {
        return lastPacketReceived;
    }

    public void sendKeepalive() throws IOException, GeneralSecurityException {
        if (System.currentTimeMillis() - getTimeOfLastPacketSent() < 750) {
            return;
        }
        if (getName() == null) {
            return;
        }
        send(new Packet(null, Packets.KEEPALIVE.id, null));
    }

    private record ResponseAction(long created, @Nonnull Consumer<Packet> action) {
    }
}
