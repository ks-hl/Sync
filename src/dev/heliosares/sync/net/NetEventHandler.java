package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;

import java.util.ArrayList;
import java.util.Objects;

public final class NetEventHandler {

    private final ArrayList<EventHandler> listeners = new ArrayList<>();
    private final SyncCore plugin;

    public NetEventHandler(SyncCore plugin) {
        this.plugin = plugin;
    }


    public void registerListener(int id, String channel, PacketConsumer consumer) {
        if (channel != null && !channel.matches("\\w+:\\w+")) {
            throw new IllegalArgumentException("Channel name must conform to 'PluginName:Channel'");
        }
        synchronized (listeners) {
            listeners.add(new EventHandler(id, channel, consumer));
        }
    }

    public void unregisterChannel(String channel) {
        synchronized (listeners) {
            listeners.removeIf(netListener -> Objects.equals(channel, netListener.channel()));
        }
    }

    void execute(String server, Packet packet) {
        packet = packet.unmodifiable();
        synchronized (listeners) {
            for (EventHandler handler : listeners) {
                if (packet.getChannel() == null) {
                    if (handler.channel() != null) {
                        continue;
                    }
                } else {
                    if (!packet.getChannel().equalsIgnoreCase(handler.channel())) {
                        continue;
                    }
                }
                if (packet.getPacketId() == handler.id()) {
                    try {
                        handler.d.execute(server, packet);
                    } catch (Throwable t) {
                        plugin.warning("Failed to pass " + packet + " to " + handler.channel());
                        plugin.print(t);
                    }
                }
            }
        }
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