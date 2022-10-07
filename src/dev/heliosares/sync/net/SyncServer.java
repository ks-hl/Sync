package dev.heliosares.sync.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import dev.heliosares.sync.SyncCoreProxy;

public class SyncServer extends NetEventHandler {
	private ServerSocket serverSocket;
	private ArrayList<ServerClientHandler> clients = new ArrayList<>();
	final SyncCoreProxy plugin;

	public SyncServer(SyncCoreProxy plugin) {
		this.plugin = plugin;
	}

	/**
	 * Send to all servers
	 * 
	 * @param packetid
	 * @param packet
	 */
	public boolean send(Packet packet) {
		return send(null, packet);
	}

	/**
	 * Same as send(Packet) but for a specific server/servers
	 * 
	 * @param server The name of the server (separated by commas for multiple), or
	 *               null or "all" for all servers.
	 * @param packet
	 * @return
	 */
	public boolean send(String server, Packet packet) {
		boolean any = false;
		synchronized (clients) {
			String servers[] = (server == null || server.equals("all")) ? null : server.split(",");
			Iterator<ServerClientHandler> it = clients.iterator();
			outer: while (it.hasNext()) {
				ServerClientHandler ch = it.next();
				if (ch.getName() == null || !ch.isConnected()) {
					continue;
				}
				contains: if (servers != null) {
					for (String other : servers) {
						if (other.equalsIgnoreCase(ch.getName())) {
							break contains;
						}
					}
					continue outer;
				}
				try {
					ch.send(packet);
					any = true;
				} catch (IOException e) {
					plugin.warning("Error while sending to: " + ch.getName() + ". Kicking");
					plugin.print(e);
					ch.close();
					it.remove();
				}
			}
		}
		return any;
	}

	/**
	 * Initializes the server. This should only be called once onEnable
	 * 
	 * @param port The port to listen to
	 * @throws IOException
	 */
	public void start(int port) throws IOException {
		plugin.runAsync(new Runnable() {
			@Override
			public void run() {
				// This loop restarts the server on failure
				while (!closed) {
					try {
						serverSocket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress());

						plugin.print("Server running on port " + port + ".");
						// This look waits for clients
						while (!closed) {
							Socket socket = serverSocket.accept();
							ServerClientHandler ch = new ServerClientHandler(plugin, SyncServer.this, socket);
							ch.connect();

							plugin.debug("Connection accepted on port " + socket.getPort());

							synchronized (clients) {
								clients.add(ch);
							}
							plugin.runAsync(ch);
						}
					} catch (SocketException e1) {
						plugin.print("Server closed.");
						return;
					} catch (IOException e1) {
						plugin.warning("Server crashed:");
						plugin.print(e1);
					} finally {
						closeTemporary();
					}
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						plugin.warning("Failed to delay");
						plugin.print(e);
					}
				}
			}
		});
	}

	/**
	 * Checks for timed out clients. This should be called about once per second.
	 * Clients will be timed out after 10 seconds of not sending any packets.
	 * 
	 * Sends a keepalive packet to all clients
	 */
	public void keepalive() {
		synchronized (clients) {
			Iterator<ServerClientHandler> it = clients.iterator();
			while (it.hasNext()) {
				ServerClientHandler ch = it.next();
				boolean remove = false;
				if (ch.getName() == null) {
					if (ch.getAge() > 3000) {
						remove = true;
					} else {
						continue;// Still connecting
					}
				} else if (!ch.isConnected()) {
					remove = true;
				} else if (System.currentTimeMillis() - ch.getTimeOfLastPacketReceived() > 10000) {
					plugin.print(ch.getName() + " timed out");
					remove = true;
				} else {
					try {
						ch.sendKeepalive();
					} catch (IOException e) {
						plugin.print(e);
						remove = true;
					}
				}
				if (remove) {
					ch.close();
					it.remove();
				}
			}
		}
	}

	private boolean closed = false;

	/**
	 * Permanently closes this server. Call only onDisable
	 */
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		closeTemporary();
	}

	/**
	 * Temporarily closes this server. This will cause the server to restart.
	 */
	public void closeTemporary() {
		synchronized (clients) {
			for (SocketConnection ch : this.clients) {
				ch.close();
			}
		}
		if (serverSocket == null || serverSocket.isClosed()) {
			return;
		}
		try {
			serverSocket.close();
		} catch (IOException e) {
			plugin.print(e);
		}
	}

	/**
	 * Closes then removes the specified SocketConnection (Client)
	 * 
	 * @param ch Connection to remove
	 */
	public void remove(SocketConnection ch) {
		ch.close();
		synchronized (clients) {
			clients.remove(ch);
		}
	}

	/**
	 * @return an unmodifiableList of all clients currently connected
	 */
	public List<ServerClientHandler> getClients() {
		return Collections.unmodifiableList(clients);
	}
}
