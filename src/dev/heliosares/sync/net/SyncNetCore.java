package dev.heliosares.sync.net;

import java.io.IOException;
import java.util.List;

public interface SyncNetCore {
	public boolean send(Packet packet) throws IOException;
	public boolean send(String server, Packet packet) throws IOException;
	public NetEventHandler getEventHandler();
	public List<String> getServers();
	public void close();
	public void closeTemporary();
	public String getName();
}
