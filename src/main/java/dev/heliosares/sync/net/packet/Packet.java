package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.IDProvider;
import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

public class Packet {
    private final String channel;
    private final PacketType type;
    private final JSONObject payload;
    private Long responseID;
    private final boolean isResponse;
    private String origin;
    private String forward;

    public Packet(String channel, PacketType type, JSONObject payload) {
        this(channel, type, payload, null, false);
    }

    /**
     * Creates a packet from a JSONObject
     *
     * @param packet The packet to parse
     * @see #toJSON()
     */
    public Packet(JSONObject packet) throws MalformedPacketException {
        if (!packet.has("typ")) throw new MalformedPacketException("No type specified");
        type = PacketType.getByID(packet.getInt("typ"));
        if (type == PacketType.KEEP_ALIVE) {
            responseID = null;
            isResponse = false;
        } else if (packet.has("dir")) { // Get it? Like rid but backwards
            responseID = packet.getLong("dir");
            isResponse = true;
        } else if (packet.has("rid")) {
            responseID = packet.getLong("rid");
            isResponse = false;
        } else {
            throw new MalformedPacketException("No response ID");
        }
        if (packet.has("ch")) channel = packet.getString("ch");
        else channel = null;

        if (packet.has("pl")) payload = packet.getJSONObject("pl");
        else payload = null;

        if (packet.has("fw")) forward = packet.getString("fw");
    }

    protected Packet(String channel, PacketType type, JSONObject payload, Long responseID, boolean isResponse) {
        this.responseID = responseID;
        this.channel = channel;
        this.type = type;
        this.payload = payload;
        this.isResponse = isResponse;
    }

    /**
     * Generates a packet to respond to this packet.
     *
     * @param payload The payload consume which to respond
     * @return The response packet
     */
    public Packet createResponse(JSONObject payload) {
        if (isResponse()) throw new IllegalArgumentException("Cannot reply to a response!");
        return type.mapper.apply(new Packet(channel, type, payload, responseID, true).toJSON()).setForward(origin);
    }

    public void assignResponseID(IDProvider provider) {
        if (responseID == null) responseID = provider.getNextID().combined();
    }

    @Override
    public String toString() {
        return toJSON().toString(2);
    }

    /**
     * typ - Packet ID
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
        if (responseID == null && getType() != PacketType.KEEP_ALIVE)
            throw new IllegalStateException("Can not send packet before assigning response ID");
        JSONObject json = new JSONObject();
        json.put("typ", type.id);
        if (responseID != null) json.put(isResponse ? "dir" : "rid", responseID);
        if (channel != null) json.put("ch", channel);
        if (payload != null) json.put("pl", payload);
        if (forward != null) json.put("fw", forward);
        return json;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        if (this.origin != null) throw new IllegalStateException("Cannot reset packet origin");
        this.origin = origin;
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

    @Deprecated
    public int getPacketId() {
        return getType().id;
    }

    public PacketType getType() {
        return type;
    }

    public JSONObject getPayload() {
        return payload;
    }

    public String getForward() {
        return forward;
    }

    public Packet setForward(String forward) {
        this.forward = forward;
        return this;
    }
}
