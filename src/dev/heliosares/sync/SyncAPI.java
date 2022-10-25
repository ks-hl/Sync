package dev.heliosares.sync;

import java.io.IOException;

import dev.heliosares.sync.bungee.SyncBungee;
import dev.heliosares.sync.net.NetListener;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.spigot.SyncSpigot;

public class SyncAPI {
	private static SyncCore instance;

	public static SyncCore getInstance() {
		if (instance != null) {
			return instance;
		}
		if ((instance = SyncSpigot.getInstance()) != null) {
			return instance;
		}
		if ((instance = SyncBungee.getInstance()) != null) {
			return instance;
		}
		return instance;
	}

	public static void send(String server, Packet packet) {
		if (getInstance() instanceof SyncBungee bungee) {
			bungee.getSync().send(server, packet);
		}
	}

	public static void send(Packet packet) throws IOException {
		getInstance().send(packet);
	}

	public void register(NetListener listen) {
		getInstance().register(listen);
	}

}
