package dev.heliosares.sync.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

import org.json.JSONException;
import org.json.JSONObject;

public class SocketConnection {
	private final Socket socket;
	private DataOutputStream out;
	private DataInputStream in;
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
		out = new DataOutputStream(socket.getOutputStream());
		in = new DataInputStream(socket.getInputStream());
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
//		try {
//			out.close();
//		} catch (Throwable e) {
//		}
//		try {
//			in.close();
//		} catch (Throwable e) {
//		}
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

	private byte[] read() throws IOException {
		for (int i = 0; i < 8; i++) {
			int next;
			while ((next = in.read()) != i)
				if (next == -1) {
					throw new SocketException("disconnected");
				}
			;
		}
		synchronized (in) {
			int size = in.readInt();
			if (size == 0) {
				return new byte[0];
			}
			return in.readNBytes(size);
		}
	}

	public Packet listen() throws IOException, JSONException, EOFException {
		synchronized (in) {
			Packet out = new Packet(new JSONObject(EncryptionManager.decryptString(read())));
			if (out.getPacketId() == Packets.BLOB.id) {
				out.setBlob(read());
			}
			this.lastPacketReceived = System.currentTimeMillis();
			return out;
		}
	}

	private void send(byte b[]) throws IOException {
		synchronized (out) {
			for (int i = 0; i < 8; i++)
				out.write(i);
			out.writeInt(b == null ? 0 : b.length);
			if (b != null && b.length > 0) {
				out.write(b);
			}
		}
	}

	public void send(Packet packet) throws IOException {
		if (closed) {
			return;
		}
		synchronized (out) {
			send(EncryptionManager.encrypt(packet.toString()));
			if (packet.getPacketId() == Packets.BLOB.id) {
				send(packet.getBlob());
			}
			out.flush();
		}
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
		if (getName() == null) {
			return;
		}
		send(new Packet(null, Packets.KEEPALIVE.id, null));
	}
}
