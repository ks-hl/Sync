package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.utils.ConcurrentCollection;

import java.util.ArrayList;
import java.util.Objects;

public final class NetEventHandler {

    private final ConcurrentCollection<EventHandler> listeners = new ConcurrentCollection<>(new ArrayList<>());
    private final SyncCore plugin;

    public NetEventHandler(SyncCore plugin) {
        this.plugin = plugin;
    }


    public void registerListener(int id, String channel, PacketConsumer consumer) {
        if (channel != null && !channel.matches("\\w+:\\w+")) {
            throw new IllegalArgumentException("Channel name must conform to 'PluginName:Channel'");
        }
        listeners.consume(listeners -> listeners.add(new EventHandler(id, channel, consumer)));
    }

    public void unregisterChannel(String channel) {
        listeners.consume(listeners -> listeners.removeIf(netListener -> Objects.equals(channel, netListener.channel())));
    }

    void execute(String server, Packet packet_) {
        Packet packet = packet_.unmodifiable();
        listeners.forEach(handler -> {
            if (!Objects.equals(packet.getChannel(), handler.channel())) return;
            if (packet.getPacketId() != handler.id()) return;

            try {
                handler.d.execute(server, packet);
            } catch (Throwable t) {
                plugin.warning("Failed to pass " + packet + " to " + handler.channel());
                plugin.print(t);
            }
        });
    }

    @FunctionalInterface
    public interface PacketConsumer {
        /**
         * @param server The sender of the packet being received
         * @param packet The packet itself {@link Packet#getPayload()}
         */
        void execute(String server, Packet packet);
    }

    record EventHandler(int id, String channel, PacketConsumer d) {
    }
}