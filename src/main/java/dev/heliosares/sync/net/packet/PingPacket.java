package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

public class PingPacket extends Packet {
    private final long created;
    private long ping;

    public PingPacket() {
        super(null, PacketType.PING, new JSONObject());

        created = System.currentTimeMillis();
    }

    public PingPacket(JSONObject json) {
        super(json);

        created = System.currentTimeMillis();
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public PingPacket createResponse(JSONObject payload) {
        return (PingPacket) super.createResponse(payload);
    }

    public PingPacket createResponse() {
        return createResponse(new JSONObject());
    }

    public void setOriginalPingTime(long ping) {
        this.ping = ping;
    }

    public long getRTT() {
        if (created < ping || ping == 0) return -1;
        return created - ping;
    }
}
