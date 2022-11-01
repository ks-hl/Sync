package dev.heliosares.sync.net;

import java.util.ArrayList;
import java.util.Iterator;

import dev.heliosares.sync.SyncCore;

public final class NetEventHandler {

	private final ArrayList<NetListener> listeners = new ArrayList<>();
	private final SyncCore plugin;

	public NetEventHandler(SyncCore plugin) {
		this.plugin = plugin;
	}

	public void registerListener(NetListener listen) {
		synchronized (listeners) {
			listeners.add(listen);
		}
	}

	public void unregisterListener(NetListener listen) {
		synchronized (listeners) {
			listeners.remove(listen);
		}
	}

	public void unregisterChannel(String channel) {
		synchronized (listeners) {
			Iterator<NetListener> it = listeners.iterator();
			for (NetListener listen; it.hasNext();) {
				listen = it.next();
				if (channel == null) {
					if (listen.getChannel() == null) {
						it.remove();
					}
					continue;
				}
				if (!channel.equalsIgnoreCase(listen.getChannel())) {
					it.remove();
				}
			}
		}
	}

	void execute(String server, Packet packet) {
		packet = packet.unmodifiable();
		synchronized (listeners) {
			for (NetListener listen : listeners) {
				if (packet.getChannel() == null) {
					if (listen.getChannel() != null) {
						continue;
					}
				} else {
					if (!packet.getChannel().equalsIgnoreCase(listen.getChannel())) {
						continue;
					}
				}
				if (packet.getPacketId() == listen.getPacketId()) {
					try {
						listen.execute(server, packet);
					} catch (Throwable t) {
						plugin.warning("Failed to pass " + packet + " to " + listen.getChannel());
						plugin.print(t);
					}
				}
			}
		}
	}
}