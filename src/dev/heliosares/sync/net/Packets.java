package dev.heliosares.sync.net;

public enum Packets {
	HANDSHAKE(0), KEEPALIVE(1), COMMAND(2), API(3);

	public final int id;

	private Packets(int id) {
		this.id = id;
	}
}
