package dev.heliosares.sync;

import dev.heliosares.sync.net.Packet;
import dev.heliosares.sync.net.Packets;
import org.json.JSONObject;
import org.junit.Test;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestMain {
    private static final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(10);

    @Test
    public void start() throws Exception {
        TestClient client1 = new TestClient("client1");
        TestServer server = new TestServer();

        server.getSync().start("localhost", 8001);
        client1.getSync().start("localhost", 8001);

        AtomicBoolean received = new AtomicBoolean(false);
        server.getSync().getEventHandler().registerListener(Packets.COMMAND.id, null, (serverSender, packet) -> received.set(true));

        long start = System.currentTimeMillis();
        while (true) {
            assert System.currentTimeMillis() - start < 3000 : "Client timed out";
            if (client1.getSync().isConnected()) {
                client1.getSync().send(new Packet(null, Packets.COMMAND.id, new JSONObject()));
                break;
            }
            //noinspection BusyWait
            Thread.sleep(10);
        }

        Thread.sleep(500);

        assert received.get();
    }

    public static ScheduledExecutorService getScheduler() {
        return scheduler;
    }
}
