package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.kshl.kshlib.concurrent.ConcurrentMap;
import dev.kshl.kshlib.encryption.EncryptionAES;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.HashMap;
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
    private final ConcurrentMap<HashMap<Long, ResponseAction>, Long, ResponseAction> responses = new ConcurrentMap<>(new HashMap<>());
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
                if (packet instanceof BlobPacket blobPacket) blobPacket.setBlob(read());
                if (packet.getType() != PacketType.KEEP_ALIVE) plugin.debug(() -> {
                    String line = "RECV";
                    if (this instanceof ServerClientHandler sch) {
                        if (packet.getForward() != null) line = "FWD";
                        line += " (" + sch.getName();
                        if (packet.getForward() != null) line += " -> " + packet.getForward();
                        line += ")";
                    }
                    line += ": " + packet;
                    return line;
                });
                if (packet.isResponse()) {
                    ResponseAction action = responses.get(packet.getResponseID());
                    try {
                        if (action != null) action.accept(packet);
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

        send(new Packet(null, PacketType.KEEP_ALIVE, null), null, 0, null);
    }

    protected void send(Packet packet, @Nullable Consumer<Packet> responseConsumer, long timeoutMillis, @Nullable Runnable timeoutAction) throws IOException {
        if (closed) return;
        if (packet.isResponse() && responseConsumer != null)
            throw new IllegalArgumentException("Cannot specify consumer for a response");
        final long sendTime = System.currentTimeMillis();
        synchronized (out) {
            if (packet instanceof PingPacket && !packet.isResponse() && (plugin instanceof SyncClient || packet.getForward() == null)) {
                final Consumer<Packet> responseConsumer_ = responseConsumer;
                responseConsumer = packet1 -> {
                    if (packet1 instanceof PingPacket pingPacket) pingPacket.setOriginalPingTime(sendTime);
                    if (responseConsumer_ != null) responseConsumer_.accept(packet1);
                };
            }
            if (responseConsumer != null) {
                ResponseAction responseAction = new ResponseAction(packet.getResponseID(), sendTime, responseConsumer, timeoutMillis > 0 ? timeoutMillis : 300000L, timeoutAction);
                responses.put(packet.getResponseID(), responseAction);
                if (timeoutAction != null) {
                    plugin.scheduleAsync(() -> {
                        ResponseAction action = responses.remove(responseAction.id());
                        if (action != null) action.timeout();
                    }, responseAction.timeoutMillis());
                }
            }
            String plain = packet.toString();
            if (packet.getType() != PacketType.KEEP_ALIVE && (packet.getForward() == null || (!(this instanceof ServerClientHandler)))) // Don't debug for forwarding packets, that was already accomplished on receipt
                plugin.debug(() -> {
                    String line = "SEND";
                    if (this instanceof ServerClientHandler sch) {
                        line += " (" + sch.getName() + ")";
                    }
                    line += ": " + packet;
                    return line;
                });
            send(plain.getBytes());
            if (packet instanceof BlobPacket blobPacket) send(blobPacket.getBlob());
            out.flush();
        }
        this.lastPacketSent = sendTime;
    }

    protected void send(byte[] b) throws IOException {
        try {
            b = encryption.encrypt(b);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
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

    protected byte[] read() throws IOException, IllegalBlockSizeException, BadPaddingException {
        return encryption.decrypt(readRaw());
    }

    public byte[] readRaw() throws IOException {
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
        responses.consume(responses -> responses.values().removeIf(ResponseAction::removeIf));
    }

    private static final class ResponseAction {
        private final long id;
        private final long created;
        @Nonnull
        private final Consumer<Packet> action;
        private final long timeoutMillis;
        private final Runnable timeoutAction;
        private boolean receivedAny;

        private ResponseAction(long id, long created, @Nonnull Consumer<Packet> action, long timeoutMillis, Runnable timeoutAction) {
            this.id = id;
            this.created = created;
            this.action = action;
            this.timeoutMillis = timeoutMillis;
            this.timeoutAction = timeoutAction;
        }

        public void accept(Packet packet) {
            receivedAny = true;
            action.accept(packet);
        }

        public long timeoutMillis() {
            return timeoutMillis;
        }

        boolean removeIf() {
            if (System.currentTimeMillis() - created < timeoutMillis) return false;
            if (timeoutAction != null) timeoutAction.run();
            return true;
        }

        public long id() {
            return id;
        }

        public void timeout() {
            if (receivedAny) return;
            if (timeoutAction == null) return;
            timeoutAction.run();
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
