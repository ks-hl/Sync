package dev.heliosares.sync.net;

import dev.heliosares.sync.SyncAPI;
import org.json.JSONObject;

@Deprecated
public class Packet extends dev.heliosares.sync.net.packet.Packet {
    @Deprecated
    public Packet(String channel, int typeID, JSONObject payload) {
        super(channel, PacketType.getByID(typeID), payload);

        SyncAPI.getInstance().print("Deprecated API usage");
        Thread.dumpStack();
    }
}
