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
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

public class TestMain {
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);

    @Test
    public void start() throws Exception {
        final TestClient client1 = new TestClient("client1");
        final TestClient client2 = new TestClient("client2");
        final TestServer server = new TestServer();

        server.getSync().start("localhost", 8001);
        server.reloadKeys(true);

        client1.getSync().start("localhost", 8001);
        client2.getSync().start("localhost", 8001);

        Thread.sleep(10);

        {
            CompletableFuture<Boolean> received = new CompletableFuture<>();
            server.getSync().getEventHandler().registerListener(PacketType.COMMAND, null, (serverSender, packet) -> received.complete(true));


            for (long start = System.currentTimeMillis(); ; ) {
                assert !client1.getSync().isClosed() : "Client shutdown";
                assert System.currentTimeMillis() - start < 3000 : "Client timed out";
                if (client1.getSync().isConnected()) {
                    client1.getSync().send(new CommandPacket("test"));
                    break;
                }
                //noinspection BusyWait
                Thread.sleep(10);
            }

            assert received.get(1000, TimeUnit.MILLISECONDS);
        }

        {
            CompletableFuture<Boolean> received = new CompletableFuture<>();
            client1.getSync().getEventHandler().registerListener(PacketType.API, null, (server1, packet) -> received.complete(packet.getPayload().get("key").equals("value")));
            server.getSync().send(new Packet(null, PacketType.API, new JSONObject().put("key", "value")));

            assert received.get(1000, TimeUnit.MILLISECONDS);
        }
        {
            CompletableFuture<Boolean> received = new CompletableFuture<>();
            UUID uuid = UUID.randomUUID();
            {
                server.getSync().getUserManager().addPlayer("test-player", uuid, "proxy", true);
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

            server.getSync().getEventHandler().registerListener(PacketType.API, "test:channel", (server1, packet) -> server.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
            client1.getSync().send(null, new Packet("test:channel", PacketType.API, new JSONObject().put("ping", "ping")), response -> received.complete(true));

            assert received.get(1000, TimeUnit.MILLISECONDS);
        }

        {
            CompletableFuture<Boolean> received1 = new CompletableFuture<>();
            client2.getSync().getEventHandler().registerListener(PacketType.API, "test:channel2", (server1, packet) -> client2.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
            client1.getSync().send("client2", new Packet("test:channel2", PacketType.API, new JSONObject().put("ping", "ping")), response -> received1.complete(true));

            CompletableFuture<Boolean> received2 = new CompletableFuture<>();
            client1.getSync().getEventHandler().registerListener(PacketType.API, "test:channel3", (server1, packet) -> client1.getSync().send(packet.createResponse(new JSONObject().put("pong", "pong"))));
            client2.getSync().send("client1", new Packet("test:channel3", PacketType.API, new JSONObject().put("ping", "ping")), response -> received2.complete(true));

            assert received1.get(1000, TimeUnit.MILLISECONDS);
            assert received2.get(1000, TimeUnit.MILLISECONDS);
        }
    }

    public static ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
