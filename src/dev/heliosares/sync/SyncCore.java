package dev.heliosares.sync;

public interface SyncCore {
	public void runAsync(Runnable run);	
	public void warning(String msg);
	public void print(String msg);
	public void print(Throwable t);
	public void debug(String msg);
}
