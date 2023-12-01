package dev.heliosares.sync;

import dev.heliosares.sync.daemon.SyncDaemon;
import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.heliosares.sync.utils.CompletableException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TestMain {
    private static final TestClient client1;
    private static final TestClient client2;
    private static final TestServer server;

    private static final int PORT = 8002;

    static {
        boolean ignored = new File("test").delete();
        try {
            long start = System.currentTimeMillis();
            client1 = new TestClient("client1");
            client2 = new TestClient("client2");
            server = new TestServer("server");

            System.out.println("instances: " + (System.currentTimeMillis() - start) + "ms");
            start = System.currentTimeMillis();

            server.getSync().start("localhost", PORT);
            server.reloadKeys(true);

            System.out.println("serverStart: " + (System.currentTimeMillis() - start) + "ms");
            start = System.currentTimeMillis();

            CompletableException<Exception> client1Completable = client1.getSync().start("localhost", PORT);
            client2.getSync().start("localhost", PORT);

            System.out.println("clientStart: " + (System.currentTimeMillis() - start) + "ms");
            start = System.currentTimeMillis();

            client1Completable.getAndThrow(3000, TimeUnit.MILLISECONDS);

            System.out.println("clientStartWait: " + (System.currentTimeMillis() - start) + "ms");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test(expected = GeneralSecurityException.class)
    public void testMissingKey() throws Exception {
        TestClient client3 = new TestClient("client3", false, null);
        CompletableException<Exception> completableException = client3.getSync().start("localhost", PORT);
        completableException.getAndThrow();
    }

    @Test(timeout = 3000)
    public void testCommandSend() throws Exception {
        long start = System.currentTimeMillis();
        CompletableFuture<Boolean> receivedClient1 = new CompletableFuture<>();
        CompletableFuture<Boolean> receivedDaemon = new CompletableFuture<>();
        server.getSync().getEventHandler().registerListener(PacketType.COMMAND, null, (serverSender, packet) -> {
            if (serverSender.equals("client1")) receivedClient1.complete(true);
            if (serverSender.equals("daemon1")) receivedDaemon.complete(true);
        });

        client1.getSync().send(new CommandPacket("test"));
        TestDaemon testDaemon = new TestDaemon("daemon1");
        server.reloadKeys(false);
        SyncDaemon.run(testDaemon, "-port:" + PORT, "hello");

        assert receivedClient1.get();
        assert receivedDaemon.get();
        System.out.println(System.currentTimeMillis() - start + "ms");

    }

    @Test(timeout = 3000)
    public void testAPIPacket() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        client1.getSync().getEventHandler().registerListener(PacketType.API, null, (server1, packet) -> received.complete(packet.getPayload().get("key").equals("value")));
        server.getSync().send(new Packet(null, PacketType.API, new JSONObject().put("key", "value")));

        assert received.get();
    }

    @Test(timeout = 3000)
    public void testAPIResponse() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        server.getSync().getEventHandler().registerListener(PacketType.API, "test:channel", (server1, packet) -> server.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
        AtomicBoolean timeOut = new AtomicBoolean();
        client1.getSync().send(null, new Packet("test:channel", PacketType.API, new JSONObject().put("ping", "ping")), response -> received.complete(true), 100, () -> timeOut.set(true));

        assert received.get();
        Thread.sleep(150);
        assert !timeOut.get();
    }

    @Test(timeout = 3000)
    public void testBlobResponse() throws Throwable {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        final String msg1 = "Hello!";
        final String msg2 = "Hello back!";
        server.getSync().getEventHandler().registerListener(PacketType.API_WITH_BLOB, "test:blob", (server1, packet) -> {
            if (!(packet instanceof BlobPacket blobPacket1)) {
                error.set(new IllegalArgumentException("Invalid packet class: " + packet.getClass().getName()));
                received.complete(false);
                return;
            }
            String blob = new String(blobPacket1.getBlob());
            if (!msg1.equals(blob)) {
                error.set(new AssertionError("Invalid response blob: " + blob));
                received.complete(false);
                return;
            }
            server.getSync().send(server1, blobPacket1.createResponse(new JSONObject()).setBlob(msg2.getBytes()));
        });
        client1.getSync().send(null, new BlobPacket("test:blob", new JSONObject()).setBlob(msg1.getBytes()), response -> {
            if (!(response instanceof BlobPacket blobPacket)) {
                error.set(new IllegalArgumentException("Invalid packet class: " + response.getClass().getName()));
                received.complete(false);
                return;
            }
            String blob = new String(blobPacket.getBlob());
            if (msg2.equals(blob)) {
                received.complete(true);
                return;
            }
            error.set(new AssertionError("Invalid response blob: " + blob));
            received.complete(false);
        });

        if (received.get()) return;
        if (error.get() != null) throw error.get();
        assert false;
    }

    @Test
    public void testPlayerDataCustom() throws Exception {
        TestClient testClient4 = new TestClient("client4");
        server.reloadKeys(false);
        server.runAsync(() -> testClient4.getSync().start("localhost", PORT));
        client2.getSync().getConnectedCompletable().getAndThrow(3000, TimeUnit.MILLISECONDS);
        UUID uuid = UUID.randomUUID();
        {
            server.getSync().getUserManager().addPlayer("testPlayer", uuid, "proxy", true);
            Thread.sleep(10);
            PlayerData playerData = server.getSync().getUserManager().getPlayer(uuid);
            assert playerData != null;
            playerData.getCustomString("test", "key1", true).set("value1");
            playerData.getCustomBoolean("test", "key2", true).set(true);
            playerData.getCustomStringSet("test", "key3", true).set(Set.of("value3"));
            playerData.getCustomBlob("test", "key4", true).set("value4".getBytes());
            playerData.setServer("server1");
            playerData.setVanished(true);
        }

        Thread.sleep(10);

        {
            PlayerData playerData = client1.getSync().getUserManager().getPlayer(uuid);
            assert playerData != null;

            assertEquals(playerData.getCustomString("test", "key1", true).get(), "value1");
            assertEquals(playerData.getCustomBoolean("test", "key2", true).get(), true);
            assertEquals(playerData.getCustomStringSet("test", "key3", true).get(), Set.of("value3"));
            assertEquals(playerData.getServer(), "server1");
            assertEquals(new String(Objects.requireNonNull(playerData.getCustomBlob("test", "key4", true).get())), "value4");
        }

        testClient4.getSync().getConnectedCompletable().getAndThrow(3000L, TimeUnit.MILLISECONDS);
        Thread.sleep(10);
        {
            PlayerData playerData = testClient4.getSync().getUserManager().getPlayer(uuid);
            assert playerData != null;

            assertEquals(playerData.getCustomString("test", "key1", true).get(), "value1");
            assertEquals(playerData.getCustomBoolean("test", "key2", true).get(), true);
            assertEquals(playerData.getCustomStringSet("test", "key3", true).get(), Set.of("value3"));

            playerData.getCustomString("test", "key1", true).set("value2");
            assert playerData.isVanished();
        }

        Thread.sleep(10);

        {
            PlayerData playerData = client1.getSync().getUserManager().getPlayer(uuid);
            assert playerData != null;
            assertEquals(playerData.getCustomString("test", "key1", true).get(), "value2");

            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomBoolean("+", "a", true));
            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomString(null, "ed", true));
            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomStringSet("a", null, true));
            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomBlob("a", "", true));
        }

    }

    @Test(timeout = 3000)
    public void testForwardedResponse() throws Exception {
        client2.getSync().getConnectedCompletable().getAndThrow(3000, TimeUnit.MILLISECONDS);

        CompletableFuture<Boolean> received1 = new CompletableFuture<>();
        client2.getSync().getEventHandler().registerListener(PacketType.API, "test:channel2", (server1, packet) -> client2.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
        client1.getSync().send("client2", new Packet("test:channel2", PacketType.API, new JSONObject().put("ping", "ping")), response -> received1.complete(true));

        CompletableFuture<Boolean> received2 = new CompletableFuture<>();
        client1.getSync().getEventHandler().registerListener(PacketType.API, "test:channel3", (server1, packet) -> client1.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
        client2.getSync().send("client1", new Packet("test:channel3", PacketType.API, new JSONObject().put("ping", "ping")), response -> received2.complete(true));

        assert received1.get();
        assert received2.get();
    }

    @Test(timeout = 200)
    public void testTimeout() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        client1.getSync().send(null, new Packet("test:void", PacketType.API, new JSONObject()), response -> {
        }, 30, () -> {
            System.out.println("Timeout received (expected)");
            received.complete(true);
        });

        assert received.get();
    }

    @Test(timeout = 10000)
    public void testStressTest() throws Exception {
        Set<CompletableFuture<Boolean>> receivedSet = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            CompletableFuture<Boolean> received1 = new CompletableFuture<>();
            receivedSet.add(received1);
            client1.getSync().send(null, new PingPacket(), response -> {
                received1.complete(true);
                client1.print("Ping: " + ((PingPacket) response).getRTT() + "ms");
            });

            CompletableFuture<Boolean> received2 = new CompletableFuture<>();
            receivedSet.add(received2);
            server.getSync().send("client1", new PingPacket(), response -> {
                received2.complete(true);
                server.print("Ping: " + ((PingPacket) response).getRTT() + "ms");
            });
        }

        for (CompletableFuture<Boolean> future : receivedSet) future.get();
    }
}