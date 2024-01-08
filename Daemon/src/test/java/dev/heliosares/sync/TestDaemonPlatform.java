package dev.heliosares.sync;

import dev.heliosares.sync.daemon.SyncDaemon;
import dev.heliosares.sync.utils.CustomLogger;

import java.io.File;
import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestDaemonPlatform extends SyncDaemon {

    private final String name;
    private final Logger logger;

    public TestDaemonPlatform(String name) {
        this.name = name;
        this.logger = CustomLogger.getLogger(name);
    }

    @Override
    public File getPublicKeyFile() {
        return new File("test/clients/" + name + ".public.key");
    }

    @Override
    public File getPrivateKeyFile() {
        return new File("test/" + name + "/private.key");
    }

    private Logger getLogger() {
        return logger;
    }

    @Override
    public void warning(String msg) {
        getLogger().warning(msg);
    }

    @Override
    public void print(String msg) {
        getLogger().info(msg);
    }

    @Override
    public void print(String message, Throwable t) {
        getLogger().log(Level.WARNING, (message == null ? "" : (message + " - ")) + t.getMessage(), t);
    }

    @Override
    public void debug(String msg) {
        print("[DEBUG] " + msg);
    }
}
