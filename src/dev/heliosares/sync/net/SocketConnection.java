package dev.heliosares.sync.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

public class SocketConnection {
	private final Socket socket;
	private PrintWriter out;
	private BufferedReader in;
	private boolean closed;
	private String name;
	private long lastPacketSent;
	private long lastPacketReceived;
	private final long created;

	public SocketConnection(Socket socket) {
		this.socket = socket;
		this.created = System.currentTimeMillis();
	}

	public long getAge() {
		return System.currentTimeMillis() - created;
	}

	public void connect() throws IOException {
		out = new PrintWriter(socket.getOutputStream(), true);
		in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
	}

	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		try {
			socket.close();
		} catch (Throwable e) {
		}
		try {
			out.close();
		} catch (Throwable e) {
		}
		try {
			in.close();
		} catch (Throwable e) {
		}
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

	public Packet listen() throws IOException, JSONException {
		Packet out = new Packet(new JSONObject(in.readLine()));
		this.lastPacketReceived = System.currentTimeMillis();
		return out;
	}

	public void send(Packet packet) throws IOException {
		out.println(packet.toString());
		this.lastPacketSent = System.currentTimeMillis();
	}

	public long getTimeOfLastPacketSent() {
		return lastPacketSent;
	}

	public long getTimeOfLastPacketReceived() {
		return lastPacketReceived;
	}

	public void sendKeepalive() throws IOException {
		if (System.currentTimeMillis() - getTimeOfLastPacketSent() < 750) {
			return;
		}
		send(new Packet(null, Packets.KEEPALIVE.id, null));
	}
}
