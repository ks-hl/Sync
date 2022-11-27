package dev.heliosares.sync.net;

import org.json.JSONObject;

public class Packet {
    private final String channel;
    private final int packetID;
    private final JSONObject payload;
    private byte[] blob;
    private String forward;

    private final long responseID;

    public boolean isResponse() {
        return isResponse;
    }

    private final boolean isResponse;

    public long getResponseID() {
        return responseID;
    }

    public Packet(String channel, int packetid, JSONObject payload) {
        this(channel, packetid, payload, IDProvider.getNextID(), false);
    }

    public Packet(String channel, int packetid, JSONObject payload, long responseID) {
        this(channel, packetid, payload, responseID, true);
    }


    public Packet(String channel, int packetid, JSONObject payload, byte[] blob) {
        this(channel, packetid, payload);
        this.blob = blob;
    }

    Packet(JSONObject packet) {
        packetID = packet.getInt("pid");
        if (packet.has("irid")) {
            responseID = packet.getLong("rid");
            isResponse = true;
        } else if (packet.has("rid")) {
            responseID = packet.getLong("rid");
            isResponse = false;
        } else {
            responseID = -1;
            isResponse = false;
        }
        if (packet.has("ch")) channel = packet.getString("ch");
        else channel = null;

        if (packet.has("pl")) payload = packet.getJSONObject("pl");
        else payload = null;

        if (packet.has("fw")) forward = packet.getString("fw");
    }

    private Packet(String channel, int packetid, JSONObject payload, long responseID, boolean isResponse) {
        this.responseID = responseID;
        this.channel = channel;
        this.packetID = packetid;
        this.payload = payload;
        this.isResponse = isResponse;
    }

    public String getChannel() {
        return channel;
    }

    public int getPacketId() {
        return packetID;
    }

    public JSONObject getPayload() {
        return payload;
    }

    @Override
    public String toString() {
        return toJSON().toString();
    }

    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("pid", packetID);
        json.put("rid", responseID);
        if (channel != null) json.put("ch", channel);
        if (payload != null) json.put("pl", payload);
        if (forward != null) json.put("fw", forward);
        return json;
    }

    public byte[] getBlob() {
        return blob;
    }

    public Packet setBlob(byte[] blob) {
        this.blob = blob;
        return this;
    }

    public String getForward() {
        return forward;
    }

    public Packet setForward(String forward) {
        this.forward = forward;
        return this;
    }

    public Packet unmodifiable() {
        return new UnmodifiablePacket(this);
    }

//    public Packet copy() {
//        return new Packet(toJSON()).setBlob(getBlob());
//    }
}
