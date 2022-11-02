package dev.heliosares.sync.net;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.stream.Collectors;

import org.json.JSONObject;

import dev.heliosares.sync.SyncCore;

public class SyncClient implements SyncNetCore {
	private SocketConnection connection;
	private final SyncCore plugin;
	private boolean closed;
	private int unableToConnectCount = 0;
	private List<String> servers;
	private final NetEventHandler eventhandler;
	private final UserManager usermanager;

	public SyncClient(SyncCore plugin) {
		this.plugin = plugin;
		this.eventhandler = new NetEventHandler(plugin);
		this.usermanager = new UserManager(plugin, this);
		eventhandler.registerListener(usermanager);
	}

	/**
	 * Initiates the client. This should only be called once, onEnable
	 * 
	 * @param ip         Currently only loopback (127.0.0.1) is supported
	 * @param port       Port of the proxy server
	 * @param serverport Port of this Minecraft server
	 * @throws IOException
	 */
	public void start(String ip, int port, int serverport) throws IOException {
		if (connection != null) {
			throw new IllegalStateException("Client already started");
		}
		plugin.runAsync(new Runnable() {
			@Override
			public void run() {
				while (!closed) {
					if (unableToConnectCount < 3 || plugin.debug()) {
						plugin.print("Client connecting on port " + port + "...");
					}
					try {
						connection = new SocketConnection(new Socket(InetAddress.getLoopbackAddress(), port));
						connection.connect();

						plugin.debug("Sending handshake");
						connection.send(
								new Packet(null, Packets.HANDSHAKE.id, new JSONObject().put("serverport", serverport)));

						while (!closed) { // Listen for packets
							Packet packet = connection.listen();
							if (packet == null) {
								plugin.warning("Null packet received");
								continue;
							}
							if (packet.getPacketId() != Packets.KEEPALIVE.id) {
								plugin.debug("received: " + packet.toString());
							}
							boolean isHandshake = packet.getPacketId() == Packets.HANDSHAKE.id;
							boolean noname = connection.getName() == null;
							if (isHandshake) {
								if (!noname) {
									plugin.warning("Server tried to handshake after connected. Reconnecting...");
									break;
								}
								connection.setName(packet.getPayload().getString("name"));
								plugin.print("Connected as " + connection.getName());
								unableToConnectCount = 0;

								usermanager.sendPlayers("all");
								usermanager.request("all");

								continue;
							}
							if (noname) {
								plugin.warning("Server tried to send packet without handshake. Reconnecting...");
								break;
							}
							if (packet.getPacketId() == Packets.SERVER_LIST.id) {
								servers = packet.getPayload().getJSONArray("servers").toList().stream()
										.map(o -> (String) o).collect(Collectors.toUnmodifiableList());
							}

							eventhandler.execute("proxy", packet);
						}
					} catch (ConnectException e) {
						if (!plugin.debug() && ++unableToConnectCount == 3) {
							plugin.print("Server not available. Continuing to attempt silently...");
						} else if (plugin.debug() || unableToConnectCount < 3) {
							plugin.print("Server not available. Retrying...");
						}
					} catch (NullPointerException | SocketException | EOFException e) {
						plugin.print("Connection closed." + (closed ? "" : " Retrying..."));
						if (plugin.debug()) {
							plugin.print(e);
						}
						if (closed) {
							return;
						}
					} catch (Exception e) {
						plugin.warning("Client crashed. Restarting...");
						plugin.print(e);
					} finally {
						closeTemporary();
					}
					try {
						Thread.sleep(unableToConnectCount > 3 ? 5000 : 1000);
					} catch (InterruptedException e) {
						plugin.warning("Failed to delay");
						plugin.print(e);
					}
				}
			}
		});
	}

	/**
	 * This should be called about once per second. Informs the server that it is
	 * still connected. If no packet is received by the server for 10 seconds, the
	 * client will be kicked.
	 * 
	 * @throws IOException
	 */
	public void keepalive() throws Exception {
		if (closed || connection == null || !connection.isConnected()) {
			return;
		}
		connection.sendKeepalive();
		if (System.currentTimeMillis() - connection.getTimeOfLastPacketReceived() > 10000) {
			closeTemporary();
			plugin.warning("timed out from proxy");
		}
	}

	/**
	 * Sends a packet!
	 * 
	 * @param packet
	 * @throws IOException
	 */
	public boolean send(Packet packet) throws IOException, GeneralSecurityException {
		if (connection.getName() == null) {
			throw new IllegalStateException("Can not send packets before handshake.");
		}
		connection.send(packet);
		return true;
	}

	/**
	 * Permanently closes this instance of the client. Only call onDisable
	 */
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		closeTemporary();
	}

	/**
	 * Will terminate the current connection to the server and cause the client to
	 * attempt to reconnect.
	 */
	public void closeTemporary() {
		if (connection == null) {
			return;
		}
		connection.close();
	}

	/**
	 * @return The name of this server according to the proxy
	 */
	public String getName() {
		return connection.getName();
	}

	public boolean isConnected() {
		if (connection == null) {
			return false;
		}
		return connection.isConnected();
	}

	public List<String> getServers() {
		return servers;
	}

	public boolean send(String server, Packet packet) throws IOException, GeneralSecurityException {
		if (server != null && !server.equals("all")) {
			if (servers == null || !servers.contains(server)) {
				return false;
			}
		}
		packet.setForward(server);
		return send(packet);
	}

	@Override
	public NetEventHandler getEventHandler() {
		return eventhandler;
	}

	public UserManager getUserManager() {
		return usermanager;
	}
}
