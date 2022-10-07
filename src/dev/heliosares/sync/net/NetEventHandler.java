package dev.heliosares.sync.net;

import java.util.ArrayList;

public class NetEventHandler {

	private final ArrayList<NetListener> listeners = new ArrayList<>();

	public void registerListener(NetListener listen) {
		synchronized (listeners) {
			listeners.add(listen);
		}
	}

	public void unregisterListener(NetListener listen) {
		synchronized (listeners) {
			listeners.add(listen);
		}
	}

	void execute(String server, Packet packet) {
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
					listen.execute(server, packet);
				}
			}
		}
	}
}