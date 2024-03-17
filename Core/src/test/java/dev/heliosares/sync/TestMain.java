package dev.heliosares.sync;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.SyncClient;
import dev.heliosares.sync.net.SyncServer;
import dev.heliosares.sync.net.packet.BlobPacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.kshl.kshlib.encryption.EncryptionRSA;
import org.json.JSONObject;
import org.junit.Test;

import javax.annotation.Nullable;
import java.io.File;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

public class TestMain {
    private static int serverID = 0;
    private static final Map<String, Integer> clientID = new HashMap<>();
    private static final EncryptionRSA.RSAPair serverRSAPair = EncryptionRSA.generate();

    static {
        delete(new File("test"));
    }

    private static void delete(File file) {
        var files = file.listFiles();
        if (files != null) for (File file_ : files) delete(file_);
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private TestServer createServer() {
        return createServer(null);
    }

    private TestServer createServer(@Nullable Function<SyncCore, SyncServer> syncServerFunction) {
        TestServer server = new TestServer("server-" + ++serverID, syncServerFunction, serverRSAPair.privateKey());

        server.getSync().start("localhost", 0);
        server.reloadKeys(true);
        try {
            server.getSync().getPortCompletable().get(3000, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Failed while awaiting server start", e);
        }
        return server;
    }

    private TestClient createClient(String name, TestServer testServer) throws Exception {
        return createClient(name, testServer, true, null);
    }

    private TestClient createClient(String name, TestServer testServer, boolean implKey, @Nullable SyncClient.CreatorFunction clientCreator) throws Exception {
        int count = clientID.compute(name, (name_, count_) -> count_ == null ? 0 : (count_ + 1));
        if (count > 0) {
            name += "-" + count;
        }
        TestClient client = new TestClient(name, implKey, clientCreator, serverRSAPair.publicKey());
        testServer.reloadKeys(false);
        client.getSync().start("localhost", testServer.getSync().getPort());
        client.getSync().getConnectedCompletable().getAndThrow(3000, TimeUnit.MILLISECONDS);
        return client;
    }

    @Test(expected = GeneralSecurityException.class, timeout = 3000)
    public void testMissingKey() throws Exception {
        TestServer server = createServer();
        createClient("client_missingkey", server, false, null);
    }

    @Test(expected = GeneralSecurityException.class, timeout = 1000)
    public void testWrongKey() throws Exception {
        TestServer server = createServer();
        TestClient goodClient = createClient("client", server);
        createClient("client44873217", server, false, ((testPlatform, encryptionRSA, serverRSAPublic) -> new SyncClient(testPlatform, encryptionRSA, serverRSAPublic) {
            // makes it so the client uses client1's key ID
            @Override
            public String getRSAUserID() {
                return goodClient.getSync().getRSAUserID();
            }
        }));
    }

    @Test(timeout = 5000)
    public void testAPIPacket() throws Exception {
        TestServer server = createServer();
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        TestClient client1 = createClient("client1", server);
        client1.getSync().getEventHandler().registerListener(PacketType.API, null, (server1, packet) -> received.complete(packet.getPayload().get("key").equals("value")));
        server.getSync().send(new Packet(null, PacketType.API, new JSONObject().put("key", "value")));

        assert received.get();
    }

    @Test(timeout = 3000)
    public void testAPIResponse() throws Exception {
        TestServer server = createServer();
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        server.getSync().getEventHandler().registerListener(PacketType.API, "test:channel", (server1, packet) -> server.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
        AtomicBoolean timeOut = new AtomicBoolean();
        TestClient client1 = createClient("client1", server);
        client1.getSync().send(null, new Packet("test:channel", PacketType.API, new JSONObject().put("ping", "ping")), response -> received.complete(true), 100, () -> timeOut.set(true));

        assert received.get();
        Thread.sleep(150);
        assert !timeOut.get();
    }

    @Test(timeout = 3000)
    public void testBlobResponse() throws Throwable {
        TestServer server = createServer();
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
        TestClient client1 = createClient("client1", server);
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

    @Test(timeout = 1000)
    public void testPlayerDataHash() throws Exception {
        TestServer server = createServer();
        TestClient client1 = createClient("client1", server);
        UUID uuid = UUID.randomUUID();

        Thread.sleep(10);
        server.getSync().getUserManager().addPlayer("testplayer141", uuid, "proxy", false);

        assertNull(client1.getSync().getUserManager().getPlayer(uuid));

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

    @Test(timeout = 6000L)
    public void testPlayerDataCustom() throws Exception {
        TestServer server = createServer();
        TestClient client1 = createClient("client", server);
        TestClient client2 = createClient("client", server);

        UUID uuid = UUID.randomUUID();
        {
            server.getSync().getUserManager().addPlayer("testPlayer", uuid, "proxy", true);
            Thread.sleep(10);
            PlayerData playerData;
            do {
                Thread.sleep(3);
                playerData = server.getSync().getUserManager().getPlayer(uuid);
            } while (playerData == null);
            playerData.getCustomString("test", "key1", true).set("value1");
            playerData.getCustomBoolean("test", "key2", true).set(true);
            playerData.getCustomStringSet("test", "key3", true).set(Set.of("value3"));
            playerData.getCustomBlob("test", "key4", true).set("value4".getBytes());
            playerData.setServer("server1");
            playerData.setVanished(true);
            playerData.setHealth(10);

            server.print(playerData.toString());
        }


        {
            PlayerData playerData;
            do {
                Thread.sleep(3);
                playerData = client1.getSync().getUserManager().getPlayer(uuid);
            } while (playerData == null);
            client1.print(playerData.toString());

            assertEquals(playerData.getCustomString("test", "key1", true).get(), "value1");
            assertEquals(playerData.getCustomBoolean("test", "key2", true).get(), true);
            assertEquals(playerData.getCustomStringSet("test", "key3", true).get(), Set.of("value3"));
            assertEquals(playerData.getServer(), "server1");
            assertEquals(new String(Objects.requireNonNull(playerData.getCustomBlob("test", "key4", true).get())), "value4");
            assertEquals(playerData.getHealth(), 10, 1E-6);
        }

        TestClient client4 = createClient("client4", server);

        {
            PlayerData playerData = await(() -> client4.getSync().getUserManager().getPlayer(uuid), 1000, "Player data timed out");
            client4.print(playerData.toString());

            assertEquals(playerData.getCustomString("test", "key1", true).get(), "value1");
            assertEquals(playerData.getCustomBoolean("test", "key2", true).get(), true);
            assertEquals(playerData.getCustomStringSet("test", "key3", true).get(), Set.of("value3"));

            playerData.getCustomString("test", "key1", true).set("value2");
            assert playerData.isVanished();
        }

        Thread.sleep(10);

        {
            PlayerData playerData_;
            do {
                Thread.sleep(3);
                playerData_ = client1.getSync().getUserManager().getPlayer(uuid);
            } while (playerData_ == null);
            client1.print(playerData_.toString());
            assertEquals(playerData_.getCustomString("test", "key1", true).get(), "value2");

            PlayerData playerData = playerData_;
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
        TestServer server = createServer();
        TestClient client1 = createClient("client", server);
        TestClient client2 = createClient("client", server);
        Thread.sleep(10);
        client1.warning("Client1 servers: " + client1.getSync().getServers().stream().reduce((a, b) -> a + "," + b).orElse(""));
        client1.warning("Client2 servers: " + client2.getSync().getServers().stream().reduce((a, b) -> a + "," + b).orElse(""));

        CompletableFuture<Boolean> received1 = new CompletableFuture<>();
        client2.getSync().getEventHandler().registerListener(PacketType.API, "test:channel2", (server1, packet) -> client2.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
        assert client1.getSync().send(client2.getSync().getName(), new Packet("test:channel2", PacketType.API, new JSONObject().put("ping", "ping")), response -> received1.complete(true));

        CompletableFuture<Boolean> received2 = new CompletableFuture<>();
        client1.getSync().getEventHandler().registerListener(PacketType.API, "test:channel3", (server1, packet) -> client1.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
        assert client2.getSync().send(client1.getSync().getName(), new Packet("test:channel3", PacketType.API, new JSONObject().put("ping", "ping")), response -> received2.complete(true));

        assert received1.get();
        assert received2.get();
    }

    @Test(timeout = 1000)
    public void testResponseTimeout() throws Exception {
        TestServer server = createServer();
        TestClient client1 = createClient("client", server);
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        client1.getSync().send(null, new Packet("test:void", PacketType.API, new JSONObject()), response -> {
        }, 30, () -> {
            System.out.println("Timeout received (expected)");
            received.complete(true);
        });

        assert received.get();
    }

    @Test(timeout = 3000)
    public void testConnectionTimeout() throws Exception {
        var server = createServer();
        var client = createClient("timeout_client1", server, true, ((testPlatform, encryptionRSA, serverRSAPublic) -> new SyncClient(testPlatform, encryptionRSA, serverRSAPublic) {
            // makes it so the client won't attempt to reconnect after being timed out
            @Override
            public void closeTemporary() {
                close();
            }
        }));

        assert client.getSync().isConnected();
        server.getSync().setTimeoutMillis(0);
        Thread.sleep(110);
        assert !client.getSync().isConnected();
    }

    @Test(timeout = 10000)
    public void spamPing() throws Exception {
        TestServer server = createServer();
        TestClient client1 = createClient("client", server);
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
            server.getSync().send(client1.getSync().getName(), new PingPacket(), response -> {
                received2.complete(true);
                server.print("Ping: " + ((PingPacket) response).getRTT() + "ms");
            });

            Thread.sleep(3);
        }

        for (CompletableFuture<Boolean> future : receivedSet) future.get();
    }

    @Test(timeout = 5000)
    public void testReplayAttack() throws Exception {
        TestServer server = createServer();
        TestClient client1 = createClient("client", server);
        var client = createClient("replay_client", server, true, ((testPlatform, encryptionRSA, serverRSAPublic) -> new SyncClient(testPlatform, encryptionRSA, serverRSAPublic) {
            // makes it so the client won't attempt to reconnect after being timed out
            @Override
            public void closeTemporary() {
                close();
            }
        }));

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

    @Test(timeout = 6000)
    public void testP2P() throws Exception {
        TestServer server = createServer(pl -> new SyncServer(pl, Map.of("p2p_client", "localhost"), serverRSAPair.privateKey()) {
            @Override
            public boolean send(@Nullable String server, Packet packet, @Nullable Consumer<Packet> responseConsumer, long timeoutMillis, @Nullable Runnable timeoutAction) {
                if (packet.getType() == PacketType.PING) {
                    throw new UnsupportedOperationException();
                }
                return super.send(server, packet, responseConsumer, timeoutMillis, timeoutAction);
            }
        });
        TestClient client = createClient("client", server);
        TestClient p2p_client = createClient("p2p_client", server);
        Thread.sleep(100);
        assert client.getSync().hasP2PConnectionTo(p2p_client.getSync().getName());

        CompletableFuture<Boolean> received2 = new CompletableFuture<>();
        client.getSync().send(p2p_client.getSync().getName(), new PingPacket(), resp -> {
            received2.complete(true);
            server.print("Ping: " + ((PingPacket) resp).getRTT() + "ms");
        });
        received2.get();
    }
}