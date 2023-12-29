package dev.heliosares.sync;

import dev.heliosares.sync.daemon.SyncDaemon;
import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.utils.CompletableException;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class TestDaemon {
    private static final int PORT = 8002;

    @Test(timeout = 3000)
    public void testDaemon() throws Exception {
        long start = System.currentTimeMillis();
        var server = new TestServer("server");
        var client1 = new TestClient("client1");

        System.out.println("instances: " + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();

        server.getSync().start("localhost", PORT);
        server.reloadKeys(true);

        System.out.println("serverStart: " + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();

        CompletableException<Exception> client1Completable = client1.getSync().start("localhost", PORT);
        System.out.println("clientStart: " + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();

        client1Completable.getAndThrow(3000, TimeUnit.MILLISECONDS);

        System.out.println("clientStartWait: " + (System.currentTimeMillis() - start) + "ms");

        start = System.currentTimeMillis();
        CompletableFuture<Boolean> receivedClient1 = new CompletableFuture<>();
        CompletableFuture<Boolean> receivedDaemon = new CompletableFuture<>();
        server.getSync().getEventHandler().registerListener(PacketType.COMMAND, null, (serverSender, packet) -> {
            if (serverSender.equals("client1")) receivedClient1.complete(true);
            if (serverSender.equals("daemon1")) receivedDaemon.complete(true);
        });

        client1.getSync().send(new CommandPacket("test"));
        TestDaemonPlatform testDaemon = new TestDaemonPlatform("daemon1");
        server.reloadKeys(false);
        SyncDaemon.run(testDaemon, "-port:" + PORT, "hello");

        assert receivedClient1.get();
        assert receivedDaemon.get();
        System.out.println(System.currentTimeMillis() - start + "ms");

    }
}
