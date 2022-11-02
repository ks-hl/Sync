package dev.heliosares.sync.net;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

public interface SyncNetCore {
	public boolean send(Packet packet) throws IOException, GeneralSecurityException;

	public boolean send(String server, Packet packet) throws IOException, GeneralSecurityException;

	public NetEventHandler getEventHandler();

	public List<String> getServers();

	public void close();

	public void closeTemporary();

	public String getName();

	public UserManager getUserManager();
}
