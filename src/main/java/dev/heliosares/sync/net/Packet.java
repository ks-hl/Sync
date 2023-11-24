package dev.heliosares.sync.net;

import org.json.JSONObject;

@Deprecated
public class Packet extends dev.heliosares.sync.net.packet.Packet {
    public Packet(String channel, int typeID, JSONObject payload) {
        super(channel, PacketType.getByID(typeID), payload);
    }
}
