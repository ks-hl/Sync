package dev.heliosares.sync;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.net.PlayerData;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.net.packet.Packet;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;

public class TestMain {
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);

    @Test
    public void start() throws Exception {
        TestClient client1 = new TestClient("client1");
        TestServer server = new TestServer();

        server.getSync().start("localhost", 8001);
        client1.getSync().start("localhost", 8001);

        SyncAPI.setInstance(server);
        server.reloadKeys(true);

        AtomicBoolean received = new AtomicBoolean(false);
        server.getSync().getEventHandler().registerListener(PacketType.COMMAND, null, (serverSender, packet) -> received.set(true));


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

        Thread.sleep(50);
        assert received.get();
        received.set(false);

        client1.getSync().getEventHandler().registerListener(PacketType.API, null, (server1, packet) -> received.set(packet.getPayload().get("key").equals("value")));
        server.getSync().send(new Packet(null, PacketType.API, new JSONObject().put("key", "value")));

        Thread.sleep(50);
        assert received.get();
        received.set(false);

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
        client1.getSync().send(null, new Packet("test:channel", PacketType.API, new JSONObject().put("ping", "ping")), response -> received.set(true));

        Thread.sleep(50);
        assert received.get();
        received.set(false);

        Thread.sleep(1100);
        assert System.currentTimeMillis() - server.getSync().getClients().get(0).getTimeOfLastPacketReceived() < 1010;
        assert System.currentTimeMillis() - client1.getSync().getTimeOfLastPacketReceived() < 1010;
    }

    public static ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
