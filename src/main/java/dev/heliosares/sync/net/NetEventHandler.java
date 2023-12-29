package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.packet.Packet;
import dev.kshl.kshlib.concurrent.ConcurrentCollection;

import java.util.ArrayList;
import java.util.Objects;

public final class NetEventHandler {

    private final ConcurrentCollection<ArrayList<EventHandler>, EventHandler> listeners = new ConcurrentCollection<>(new ArrayList<>());
    private final SyncCore plugin;

    public NetEventHandler(SyncCore plugin) {
        this.plugin = plugin;
    }


    @Deprecated
    public void registerListener(int id, String channel, PacketConsumer consumer) {
        registerListener(PacketType.getByID(id), channel, consumer);
    }

    public void registerListener(PacketType type, String channel, PacketConsumer consumer) {
        if (channel != null && !channel.matches("\\w+:\\w+")) {
            throw new IllegalArgumentException("Channel name must conform to 'PluginName:Channel'");
        }
        listeners.consume(listeners -> listeners.add(new EventHandler(type, channel, consumer)));
    }

    public void unregisterChannel(String channel) {
        listeners.consume(listeners -> listeners.removeIf(netListener -> Objects.equals(channel, netListener.channel())));
    }

    void execute(String server, Packet packet) {
        listeners.forEach(handler -> {
            if (!Objects.equals(packet.getChannel(), handler.channel())) return;
            if (packet.getType() != handler.type()) return;

            try {
                handler.d.execute(server, packet);
            } catch (Throwable t) {
                plugin.print("Failed to pass " + packet + " to " + handler.channel(), t);
            }
        });
    }

    @FunctionalInterface
    public interface PacketConsumer {
        /**
         * @param server The sender of the packet being received
         * @param packet The packet itself {@link Packet#getPayload()}
         */
        void execute(String server, Packet packet) throws Exception;
    }

    record EventHandler(PacketType type, String channel, PacketConsumer d) {
    }
}