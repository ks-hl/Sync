package dev.heliosares.sync;

import java.io.IOException;

import dev.heliosares.sync.net.NetListener;
import dev.heliosares.sync.net.Packet;

public interface SyncCore {
	public void runAsync(Runnable run);	
	public void warning(String msg);
	public void print(String msg);
	public void print(Throwable t);
	public void debug(String msg);
	public boolean debug();
	public MySender getSender(String name);
	public void dispatchCommand(MySender sender, String command);
	public void send(Packet packet) throws IOException;
	public void register(NetListener listen);
}
