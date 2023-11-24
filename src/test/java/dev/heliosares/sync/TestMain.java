package dev.heliosares.sync;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.net.packet.Packet;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TestMain {
    private static final TestClient client1;
    private static final TestClient client2;
    private static final TestServer server;

    static {
        //boolean ignored = new File("test").delete();
        try {
            client1 = new TestClient("client1");
            client2 = new TestClient("client2");
            server = new TestServer();

            server.getSync().start("localhost", 8001);
            server.reloadKeys(true);

            client1.getSync().start("localhost", 8001);
            client2.getSync().start("localhost", 8001);

            Thread.sleep(10);
            long start = System.currentTimeMillis();
            while (!client1.getSync().isConnected()) {
                assert !client1.getSync().isClosed() : "Client1 shutdown";
                assert System.currentTimeMillis() - start < 3000 : "Client1 timed out";
                //noinspection BusyWait
                Thread.sleep(10);
            }
            while (!client2.getSync().isConnected()) {
                assert !client2.getSync().isClosed() : "Client2 shutdown";
                assert System.currentTimeMillis() - start < 3000 : "Client2 timed out";
                //noinspection BusyWait
                Thread.sleep(10);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void testMissingKey() throws Exception {
        TestClient client3 = new TestClient("client3", false, null);
        client3.getSync().start("localhost", 8001);
        Thread.sleep(500);
        assert !client3.getSync().isHandShookComplete();
    }

    @Test
    public void testNoHandshake() throws Exception {
        TestClient clientNoHandshake = new TestClient("clientNoHandshake", false, (PenSyncClient::new));
        assertThrows(IllegalStateException.class, () -> clientNoHandshake.getSync().start("localhost", 8001));
    }

    @Test(timeout = 1000)
    public void testCommandSend() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        server.getSync().getEventHandler().registerListener(PacketType.COMMAND, null, (serverSender, packet) -> received.complete(true));

        client1.getSync().send(new CommandPacket("test"));

        assert received.get();
    }

    @Test(timeout = 1000)
    public void testAPIPacket() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        client1.getSync().getEventHandler().registerListener(PacketType.API, null, (server1, packet) -> received.complete(packet.getPayload().get("key").equals("value")));
        server.getSync().send(new Packet(null, PacketType.API, new JSONObject().put("key", "value")));

        assert received.get();
    }

    @Test(timeout = 1000)
    public void testAPIResponse() throws Exception {
        CompletableFuture<Boolean> received = new CompletableFuture<>();
        server.getSync().getEventHandler().registerListener(PacketType.API, "test:channel", (server1, packet) -> server.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
        client1.getSync().send(null, new Packet("test:channel", PacketType.API, new JSONObject().put("ping", "ping")), response -> received.complete(true));

        assert received.get();
    }

    @Test
    public void testPlayerDataCustom() throws Exception {
        UUID uuid = UUID.randomUUID();
        {
            server.getSync().getUserManager().addPlayer("test-player", uuid, "proxy", true);
            Thread.sleep(50);
            PlayerData playerData = server.getSync().getUserManager().getPlayer(uuid);
            assert playerData != null;
            playerData.setCustom("key1", "value1");
            playerData.setCustom("key2", true);
            playerData.setCustom("key3", Set.of("value3"));
        }

        Thread.sleep(50);

        {
            PlayerData playerData = client1.getSync().getUserManager().getPlayer(uuid);
            assert playerData != null;

            assertEquals(playerData.getCustomString("key1"), "value1");
            assertEquals(playerData.getCustomBoolean("key2"), true);
            assertEquals(playerData.getCustomStringSet("key3"), Set.of("value3"));
        }
    }

    @Test(timeout = 1000)
    public void testForwardedResponse() throws Exception {
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
        }, 100, () -> {
            System.out.println("Timeout received (expected)");
            received.complete(true);
        });

        assert received.get();
    }
}