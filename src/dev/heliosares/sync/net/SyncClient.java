package dev.heliosares.sync.net;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;

import org.json.JSONObject;

import dev.heliosares.sync.SyncCore;

public class SyncClient extends NetEventHandler {
	private SocketConnection connection;
	private final SyncCore plugin;
	private boolean closed;
	private int unableToConnectCount = 0;

	public SyncClient(SyncCore plugin) {
		this.plugin = plugin;
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
						send(new Packet(null, Packets.HANDSHAKE.id, new JSONObject().put("serverport", serverport)));

						while (!closed) { // Listen for packets
							Packet packet = connection.listen();
							plugin.debug("received: " + packet.toString());
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
								continue;
							}
							if (noname) {
								plugin.warning("Server tried to send packet without handshake. Reconnecting...");
								break;
							}

							// TODO parse

							execute("proxy", packet);
						}
					} catch (ConnectException e) {
						if (!plugin.debug() && ++unableToConnectCount == 3) {
							plugin.print("Server not available. Continuing to attempt silently...");
						} else if (plugin.debug() || unableToConnectCount < 3) {
							plugin.print("Server not available. Retrying...");
						}
					} catch (NullPointerException | SocketException e) {
						plugin.print("Server closed." + (closed ? "" : " Retrying..."));
						if (closed) {
							return;
						}
					} catch (IOException e) {
						plugin.warning("Client crashed. Restarting...");
						plugin.print(e);
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
	 * This should be called about once per second. Informs the server that it is
	 * still connected. If no packet is received by the server for 10 seconds, the
	 * client will be kicked.
	 * 
	 * @throws IOException
	 */
	public void keepalive() throws IOException {
		connection.sendKeepalive();
		if (System.currentTimeMillis() - connection.getTimeOfLastPacketReceived() > 10000) {
			connection.close();
			plugin.warning("timed out from proxy");
		}
	}

	/**
	 * Sends a packet!
	 * 
	 * @param packet
	 * @throws IOException
	 */
	public void send(Packet packet) throws IOException {
		connection.send(packet);
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
	private void closeTemporary() {
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
}
