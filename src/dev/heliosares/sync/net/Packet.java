package dev.heliosares.sync.net;

import org.json.JSONObject;

public class Packet {
    private final String channel;
    private final int packetID;
    private final JSONObject payload;
    private final long responseID;
    private final boolean isResponse;
    private String origin;
    private byte[] blob;
    private String forward;

    public Packet(String channel, int packetID, JSONObject payload) {
        this(channel, packetID, payload, IDProvider.getNextID(), false);
    }

    public Packet(String channel, int packetID, JSONObject payload, long responseID) {
        this(channel, packetID, payload, responseID, true);
    }

    /**
     * @deprecated Use {@link #Packet(String, int, JSONObject)} instead
     */
    @Deprecated
    public Packet(String channel, int packetID, JSONObject payload, byte[] blob) {
        this(channel, packetID, payload);
        this.blob = blob;
    }

    /**
     * Creates a packet from a JSONObject
     *
     * @param packet The packet to parse
     * @see #toJSON()
     */
    Packet(JSONObject packet) {
        packetID = packet.getInt("pid");
        if (packet.has("dir")) {
            responseID = packet.getLong("dir");
            isResponse = true;
        } else if (packet.has("rid")) {
            responseID = packet.getLong("rid");
            isResponse = false;
        } else {
            responseID = Long.MIN_VALUE;
            isResponse = false;
        }
        if (packet.has("ch")) channel = packet.getString("ch");
        else channel = null;

        if (packet.has("pl")) payload = packet.getJSONObject("pl");
        else payload = null;

        if (packet.has("fw")) forward = packet.getString("fw");
    }

    void setOrigin(String origin) {
        if (this.origin != null) throw new IllegalStateException("Cannot reset packet origin");
        this.origin = origin;
    }

    private Packet(String channel, int packetID, JSONObject payload, long responseID, boolean isResponse) {
        this.responseID = responseID;
        this.channel = channel;
        this.packetID = packetID;
        this.payload = payload;
        this.isResponse = isResponse;
    }

    public String getOrigin() {
        return origin;
    }

    /**
     * Generates a packet to respond to this packet.
     *
     * @param channel  See constructors
     * @param packetID See constructors
     * @param payload  See constructors
     * @return The response packet
     */
    public Packet createResponse(String channel, int packetID, JSONObject payload) {
        return new Packet(channel, packetID, payload, responseID, true);
    }

    public boolean isResponse() {
        return isResponse;
    }

    public long getResponseID() {
        return responseID;
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


    /**
     * pid - Packet ID
     * <br>
     * rid - Response ID
     * <br>
     * dir - Reply to Response ID
     * <br>
     * ch  - Channel
     * <br>
     * pl  - Payload
     * <br>
     * fw  - Forwarding Server
     * <br>
     */
    public JSONObject toJSON() {
        JSONObject json = new JSONObject();
        json.put("pid", packetID);
        if (responseID > Long.MIN_VALUE) json.put(isResponse ? "dir" : "rid", responseID);
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
