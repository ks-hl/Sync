package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncCore;

import java.io.IOException;
import java.net.Socket;
import java.util.Map;
import java.util.function.Supplier;

public class P2PServer extends SyncServer {
    private final Supplier<IDProvider> idProviderSupplier;

    public P2PServer(SyncCore plugin, Supplier<IDProvider> idProviderSupplier) {
        super(plugin, Map.of(), null);
        this.idProviderSupplier = idProviderSupplier;
    }

    public void start() {
        super.start(null, 0);
    }

    @Override
    protected ServerClientHandler accept(Socket socket) throws IOException {
        return new P2PClientHandler(plugin, this, socket, idProviderSupplier);
    }
}
