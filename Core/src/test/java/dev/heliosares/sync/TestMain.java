package dev.heliosares.sync;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.net.packet.BlobPacket;
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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

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
    public void testPlayerDataHash() throws Exception {
        UUID uuid = UUID.randomUUID();

        server.getSync().getUserManager().addPlayer("testplayer141", uuid, "proxy", false);

        Thread.sleep(10);

        assert client1.getSync().getUserManager().getPlayer(uuid) == null;

        server.getSync().getUserManager().sendHash();

        await(() -> client1.getSync().getUserManager().getPlayer(uuid), 1000, "Player data timed out");
    }

    public static <T> T await(Supplier<T> supplier, long timeout, String message) throws TimeoutException {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeout) {
            var out = supplier.get();
            if (out != null) return out;
            try {
                //noinspection BusyWait
                Thread.sleep(5);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw new TimeoutException(message);
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
            playerData.setHealth(10);

            server.print(playerData.toString());
        }

        Thread.sleep(10);

        {
            PlayerData playerData = client1.getSync().getUserManager().getPlayer(uuid);
            assert playerData != null;
            client1.print(playerData.toString());

            assertEquals(playerData.getCustomString("test", "key1", true).get(), "value1");
            assertEquals(playerData.getCustomBoolean("test", "key2", true).get(), true);
            assertEquals(playerData.getCustomStringSet("test", "key3", true).get(), Set.of("value3"));
            assertEquals(playerData.getServer(), "server1");
            assertEquals(new String(Objects.requireNonNull(playerData.getCustomBlob("test", "key4", true).get())), "value4");
            assertEquals(playerData.getHealth(), 10, 1E-6);
        }

        testClient4.getSync().getConnectedCompletable().getAndThrow(3000L, TimeUnit.MILLISECONDS);
        {
            PlayerData playerData = await(() -> testClient4.getSync().getUserManager().getPlayer(uuid), 1000, "Player data timed out");
            testClient4.print(playerData.toString());

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
            client1.print(playerData.toString());
            assertEquals(playerData.getCustomString("test", "key1", true).get(), "value2");

            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomBoolean("+", "a", true));
            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomString(null, "ed", true));
            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomStringSet("a", null, true));
            assertThrows(IllegalArgumentException.class, () -> playerData.getCustomBlob("a", "", true));
        }

        server.getSync().getUserManager().removePlayer(uuid);

        Thread.sleep(10);

        assert client1.getSync().getUserManager().getPlayer(uuid) == null;
        assert client2.getSync().getUserManager().getPlayer(uuid) == null;

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
    public void testResponseTimeout() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        client1.getSync().send(null, new Packet("test:void", PacketType.API, new JSONObject()), response -> {
        }, 30, () -> {
            System.out.println("Timeout received (expected)");
            received.complete(true);
        });

        assert received.get();
    }

    @Test(timeout = 1000)
    public void testConnectionTimeout() throws Exception {
        long start = System.currentTimeMillis();
        var client = new TestClient("timeout_client1", true, ((testPlatform, encryptionRSA) -> new SyncClient(testPlatform, encryptionRSA) {
            // makes it so the client won't attempt to reconnect after being timed out
            @Override
            public void closeTemporary() {
                close();
            }
        }));
        var server = new TestServer("timeout_server");

        System.out.println("instances: " + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();

        server.getSync().start("localhost", PORT + 1);
        server.reloadKeys(true);

        System.out.println("serverStart: " + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();

        CompletableException<Exception> client1Completable = client.getSync().start("localhost", PORT + 1);

        System.out.println("clientStart: " + (System.currentTimeMillis() - start) + "ms");
        start = System.currentTimeMillis();

        client1Completable.getAndThrow(3000, TimeUnit.MILLISECONDS);

        System.out.println("clientStartWait: " + (System.currentTimeMillis() - start) + "ms");

        assert client.getSync().isConnected();
        server.getSync().setTimeoutMillis(0);
        Thread.sleep(110);
        assert !client.getSync().isConnected();
    }

    @Test(timeout = 10000)
    public void testStressTest() throws Exception {
        Set<CompletableFuture<Boolean>> receivedSet = new HashSet<>();
        for (int i = 0; i < 100; i++) {
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

            Thread.sleep(3);
        }

        for (CompletableFuture<Boolean> future : receivedSet) future.get();
    }

    @Test
    public void testReplayAttack() throws Exception {
        var client = new TestClient("replay_client1", true, ((testPlatform, encryptionRSA) -> new SyncClient(testPlatform, encryptionRSA) {
            // makes it so the client won't attempt to reconnect after being timed out
            @Override
            public void closeTemporary() {
                close();
            }
        }));
        CompletableException<Exception> client1Completable = client.getSync().start("localhost", PORT);
        client1Completable.getAndThrow(3000, TimeUnit.MILLISECONDS);

        PingPacket packet = new PingPacket();
        AtomicInteger responseCount = new AtomicInteger();
        client.getSync().send(null, packet, response -> {
            responseCount.incrementAndGet();
            client1.print("Ping: " + ((PingPacket) response).getRTT() + "ms");
        });
        Thread.sleep(15);
        client.getSync().send(null, packet);
        Thread.sleep(15);
        client.getSync().close();
        assert responseCount.get() > 0 : "Normal packet did not receive a response.";
        assert responseCount.get() == 1 : "Replay packet received a response.";
    }
}