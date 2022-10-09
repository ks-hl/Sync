package dev.heliosares.sync.net;

import java.io.IOException;
import dev.heliosares.sync.SyncCoreProxy;

public class Test {

	public static void main(String[] args) {
		Object o = "test";
		if (o instanceof String str) {
			System.out.println(str);
		}
		System.exit(0);
		SyncClient client = new SyncClient(new SyncImpl("pen", false));
		try {
			client.start("10.0.192.10", 8001, 25566);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static class SyncImpl implements SyncCoreProxy {
		private final String label;
		private boolean server;

		public SyncImpl(String label, boolean server) {
			this.label = label;
			this.server = server;
		}

		@Override
		public void runAsync(Runnable run) {
			new Thread(run).start();
		}

		@Override
		public String getServerNameByPort(int port) {
			if (!server) {
				return null;
			}
			if (port == 69) {
				return "nice";
			}
			if (port == 70) {
				return "niceplus1";
			}
			return null;
		}

		private String bracket() {
			return "[" + label + "]: ";
		}

		@Override
		public void warning(String msg) {
			System.err.println(bracket() + msg);
		}

		@Override
		public void print(String msg) {
			System.out.println(bracket() + msg);
		}

		@Override
		public void print(Throwable t) {
			System.err.println(bracket());
			t.printStackTrace();
		}

		@Override
		public void debug(String msg) {
			print(msg);
		}

		@Override
		public boolean debug() {
			return true;
		}

	}
}
