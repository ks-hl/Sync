package dev.heliosares.sync.daemon;

import org.json.JSONObject;

import dev.heliosares.sync.SyncCore;
import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.utils.CommandParser;

public class Main implements SyncCore {

	public static void main(String[] args) {
		if (args == null || args.length == 0) {
			System.err.println("You must specify arguments");
			return;
		}
		int port = 8001;
		boolean portTerm = false;
		if (args[0].startsWith("-port:")) {
			try {
				port = Integer.parseInt(args[0].substring("-port:".length()));
				portTerm = true;
			} catch (NumberFormatException e) {
				System.err.println("Invalid parameter: " + args[0]);
				return;
			}
		}
		String command = CommandParser.concat(portTerm ? 1 : 0, args);
		System.out.println("Sending: " + command);

		SyncClient sync = new SyncClient(new Main());
		try {
			sync.start("127.0.0.1", port, -1);
			while (!sync.isConnected() || sync.getName() == null) {
				Thread.sleep(10);
			}
			sync.send(new Packet(null, Packets.COMMAND.id, new JSONObject().put("command", command)));
			sync.close();
		} catch (Exception e1) {
			System.err.println("Error while enabling.");
			e1.printStackTrace();
			System.exit(1);
			return;
		}
		System.out.println("Command sent.");
		System.exit(0);
	}

	public Main() {
	}

	@Override
	public void runAsync(Runnable run) {
		new Thread(run).start();
	}

	@Override
	public void warning(String msg) {
		System.err.println(msg);
	}

	@Override
	public void print(String msg) {
		System.out.println(msg);
	}

	@Override
	public void print(Throwable t) {
		t.printStackTrace();
	}

	@Override
	public void debug(String msg) {
		print(msg);
	}
}
