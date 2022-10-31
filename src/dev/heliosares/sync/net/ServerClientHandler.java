package dev.heliosares.sync.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.json.JSONObject;

import dev.heliosares.sync.SyncCoreProxy;

public class ServerClientHandler extends SocketConnection implements Runnable {

	private final SyncCoreProxy plugin;
	private final SyncServer server;

	public ServerClientHandler(SyncCoreProxy plugin, SyncServer server, Socket socket) {
		super(socket);
		this.plugin = plugin;
		this.server = server;
	}

	@Override
	public void run() {
		while (isConnected()) {
			try {
				Packet packet = listen();
				if (packet.getPacketId() != Packets.KEEPALIVE.id) {
					plugin.debug("received from " + getName() + ": " + packet.toString());
				}
				boolean noname = getName() == null;
				if (packet.getPacketId() == Packets.HANDSHAKE.id) {
					if (!noname) {
						// TODO verbose
						plugin.warning("Client tried to handshake after connected. Disconnecting");
						close();
						return;
					}
					int port = packet.getPayload().getInt("serverport");
					String name = port == -1 ? ("daemon" + (System.currentTimeMillis() % 1000000))
							: plugin.getServerNameByPort(port);
					setName(name);

					plugin.print(name + " connected.");

					send(new Packet(null, Packets.HANDSHAKE.id, new JSONObject().put("name", name)));

					server.updateClientsWithServerList();
					continue;
				} else if (noname) {
					plugin.warning("Client tried to send packet without handshake. Disconnecting");
					close();
					return;
				} else if (packet.getPacketId() != Packets.KEEPALIVE.id) {
					if (packet.getForward() == null) {
						server.getEventHandler().execute(getName(), packet);
					} else {
						String forward = packet.getForward();
						packet.setForward(getName());
						if (forward.equalsIgnoreCase("all")) {
							server.getServers().forEach((c) -> server.send(c, packet));
						} else {
							server.send(forward, packet);
						}
					}
				}
			} catch (NullPointerException | SocketException | EOFException e1) {
				break;
			} catch (IOException e) {
				plugin.print(e);
			}
		}
		if (getName() != null) {
			plugin.print(getName() + " disconnected.");
		}
		close();
		server.remove(this);
	}
}
