package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;

public class P2PServer extends SyncServer {
    public P2PServer(SyncCore plugin) {
        super(plugin, Map.of());
    }

    public void start() {
        super.start(null, 0);
    }

    @Override
    protected ServerClientHandler accept(Socket socket) throws IOException {
        return new P2PClientHandler(plugin, this, socket);
    }
}
