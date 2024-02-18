package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.kshl.kshlib.concurrent.ConcurrentMap;
import dev.kshl.kshlib.encryption.EncryptionAES;
import dev.kshl.kshlib.misc.Formatter;
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
import java.util.LinkedList;
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
    private final ConcurrentMap<HashMap<Long, ResponseAction>, Long, ResponseAction> responses = new ConcurrentMap<>(new HashMap<>());
    private boolean closed;
    private String name;
    private long lastPacketSent = System.currentTimeMillis();
    private long lastPacketReceived = System.currentTimeMillis();
    private long lastCleanup;
    private Map<Short, LinkedList<Long>> packetIDChain = new HashMap<>();

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
        synchronized (in) {
            PacketBytes packetBytes = read();
            Packet packet = PacketType.getPacketFromJSON(new JSONObject(new String(packetBytes.decrypted())));
            PacketBytes blobBytes_ = null;
            if (packet instanceof BlobPacket blobPacket) {
                blobBytes_ = read();
                blobPacket.setBlob(blobBytes_.decrypted());
            }

            if (packet.getResponseID() == null) {
                throw new IllegalArgumentException("responseID=null for " + packet);
            }

            LinkedList<Long> last = packetIDChain.computeIfAbsent(packet.getResponseID().connectionID(), o -> new LinkedList<>());

            if (!last.isEmpty() && packet.getResponseID().packetID() <= last.getLast()) {
                if (last.getLast() - packet.getResponseID().packetID() > 10 || last.contains(packet.getResponseID().packetID())) {
                    throw new IllegalArgumentException(String.format("%s already received. Replay attack? (%d!=[%s], packet=%s)", packet.getResponseID(), packet.getResponseID().packetID(), last.stream().map(Object::toString).reduce((a, b) -> a + "," + b).orElse(""), packet));
                }
            }
            last.addLast(packet.getResponseID().packetID());
            while (last.size() > 1) {
                Long oldest = last.getFirst();
                if (Math.abs(packet.getResponseID().packetID() - oldest) > 10) {
                    last.removeFirst();
                } else break;
            }

            PacketBytes blobBytes = blobBytes_;

            this.lastPacketReceived = System.currentTimeMillis();

            if (packet.getType() != PacketType.KEEP_ALIVE && packet.getType() == PacketType.PING /* TODO remove */) {
                plugin.debug(() -> {
                    String line = "RECV";
                    if (this instanceof ServerClientHandler sch) {
                        if (packet.getForward() != null) line = "FWD";
                        line += " (" + sch.getName();
                        if (packet.getForward() != null) line += " -> " + packet.getForward();
                        line += ")";
                    }
                    line += " (" + Formatter.byteSizeToString(packetBytes.encrypted().length);
                    if (blobBytes != null) line += "+" + Formatter.byteSizeToString(blobBytes.encrypted().length);
                    line += ")";
                    line += ": " + packet;
                    return line;
                });
            }

            if (packet.isResponse()) {
                plugin.runAsync(() -> {
                    ResponseAction action = responses.get(packet.getReplyToResponseID().combined());
                    try {
                        if (action != null) action.accept(packet);
                    } catch (Throwable t) {
                        plugin.print("Error while handling response packet " + packet, t);
                    }
                });
            }

            cleanup();
            return packet;
        }
    }

    public void sendKeepAlive(IDProvider idProvider) throws IOException {
        if (System.currentTimeMillis() - getTimeOfLastPacketSent() < 500) return;
        if (idProvider == null) return;

        if (getName() == null) return;

        Packet packet = new Packet(null, PacketType.KEEP_ALIVE, null);
        packet.assignResponseID(idProvider);
        send(packet, null, 0, null);
    }

    protected void send(Packet packet, @Nullable Consumer<Packet> responseConsumer, long timeoutMillis, @Nullable Runnable timeoutAction) throws IOException {
        if (closed) return;
        if (packet.isResponse() && responseConsumer != null)
            throw new IllegalArgumentException("Cannot specify consumer for a response");
        final long sendTime = System.nanoTime();
        synchronized (out) {
            if (packet instanceof PingPacket && !packet.isResponse() && (plugin instanceof SyncClient || packet.getForward() == null)) {
                final Consumer<Packet> responseConsumer_ = responseConsumer;
                responseConsumer = packet1 -> {
                    if (packet1 instanceof PingPacket pingPacket) pingPacket.setOriginalPingTime(sendTime);
                    if (responseConsumer_ != null) responseConsumer_.accept(packet1);
                };
            }
            if (responseConsumer != null) {
                ResponseAction responseAction = new ResponseAction(packet.getResponseID().combined(), sendTime, responseConsumer, timeoutMillis > 0 ? timeoutMillis : 300000L, timeoutAction);
                responses.put(packet.getResponseID().combined(), responseAction);
                if (timeoutAction != null) {
                    plugin.scheduleAsync(() -> {
                        ResponseAction action = responses.remove(responseAction.id());
                        if (action != null) action.timeout();
                    }, responseAction.timeoutMillis());
                }
            }
            String plain = packet.toString();
            PacketBytes packetBytes = send(plain.getBytes());
            PacketBytes blobBytes_ = null;
            if (packet instanceof BlobPacket blobPacket) blobBytes_ = send(blobPacket.getBlob());
            final PacketBytes blobBytes = blobBytes_;
            if (packet.getType() != PacketType.KEEP_ALIVE && packet.getType() == PacketType.PING /* TODO remove */ && (packet.getForward() == null || (!(this instanceof ServerClientHandler)))) { // Don't debug for forwarding packets, that was already accomplished on receipt
                plugin.debug(() -> {
                    String line = "SEND";
                    if (this instanceof ServerClientHandler sch) {
                        line += " (" + sch.getName() + ")";
                    }
                    line += " (" + Formatter.byteSizeToString(packetBytes.encrypted().length);
                    if (blobBytes != null) line += "+" + Formatter.byteSizeToString(blobBytes.encrypted().length);
                    line += ")";
                    line += ": " + packet;
                    return line;
                });
            }
            out.flush();
        }
        this.lastPacketSent = System.currentTimeMillis();
    }

    protected PacketBytes send(byte[] plain) throws IOException {
        byte[] ciphertext;
        try {
            ciphertext = encryption.encrypt(plain);
        } catch (IllegalBlockSizeException | BadPaddingException e) {
            throw new IOException("Invalid session key. This is unexpected..");
        }
        sendRaw(ciphertext);
        return new PacketBytes(plain, ciphertext);
    }

    protected void sendRaw(byte[] plain) throws IOException {
        synchronized (out) {
            out.writeInt(plain == null ? 0 : plain.length);
            if (plain != null && plain.length > 0) {
                out.write(plain);
            }
        }
    }

    protected record PacketBytes(byte[] decrypted, byte[] encrypted) {
    }

    protected PacketBytes read() throws IOException, IllegalBlockSizeException, BadPaddingException {
        byte[] ciphertext = readRaw();
        return new PacketBytes(encryption.decrypt(ciphertext), ciphertext);
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
