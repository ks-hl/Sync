package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.IDProvider;
import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

import java.util.function.Consumer;

public class Packet {
    private final String channel;
    private final PacketType type;
    private final JSONObject payload;
    private IDProvider.ID responseID;
    private IDProvider.ID replyToResponseID;
    private String origin;
    private String forward;

    public Packet(String channel, PacketType type, JSONObject payload) {
        this(channel, type, payload, null, false);
    }

    public Packet(String channel, PacketType type) {
        this(channel, type, new JSONObject(), null, false);
    }

    /**
     * Creates a packet from a JSONObject
     *
     * @param packet The packet to parse
     * @see #toJSON()
     */
    public Packet(JSONObject packet) throws MalformedPacketException {
        if (!packet.has("ty")) throw new MalformedPacketException("No type specified");
        type = PacketType.getByID(packet.getInt("ty"));

        if (packet.has("rid")) {
            responseID = IDProvider.parse(packet.getLong("rid"));
        }
        if (packet.has("rtr")) {
            replyToResponseID = IDProvider.parse(packet.getLong("rtr"));
        }
        if (packet.has("ch")) channel = packet.getString("ch");
        else channel = null;

        if (packet.has("pl")) payload = packet.getJSONObject("pl");
        else payload = new JSONObject();

        if (packet.has("fw")) forward = packet.getString("fw");
        if (packet.has("or")) origin = packet.getString("or");
    }

    protected Packet(String channel, PacketType type, JSONObject payload, Long responseID, boolean isResponse) {
        if (responseID != null) {
            if (isResponse) {
                this.replyToResponseID = IDProvider.parse(responseID);
            } else {
                this.responseID = IDProvider.parse(responseID);
            }
        }
        this.channel = channel;
        this.type = type;
        this.payload = payload;
    }

    /**
     * Generates a packet to respond to this packet.
     *
     * @param payload The payload consume which to respond
     * @return The response packet
     */
    public Packet createResponse(JSONObject payload) {
        if (isResponse()) throw new IllegalArgumentException("Cannot reply to a response!");
        return type.mapper.apply(new Packet(channel, type, payload, responseID.combined(), true).toJSON(true)).setForward((origin == null || origin.equals("proxy")) ? null : origin);
    }

    public void assignResponseID(IDProvider provider) {
        if (responseID == null) responseID = provider.getNextID();
    }

    @Override
    public String toString() {
        return toJSON(true).toString(2);
    }

    /**
     * typ - Packet ID
     * <br>
     * rid - Response ID
     * <br>
     * rtr - Reply to Response ID
     * <br>
     * ch  - Channel
     * <br>
     * pl  - Payload
     * <br>
     * fw  - Forwarding Server
     * <br>
     */
    public JSONObject toJSON() {
        return toJSON(false);
    }

    public JSONObject toJSON(boolean allowNullResponseID) {
        if (!allowNullResponseID && responseID == null)
            throw new IllegalStateException("Can not send packet before assigning response ID");
        JSONObject json = new JSONObject();
        json.put("ty", type.id);
        if (responseID != null) json.put("rid", responseID.combined());
        if (replyToResponseID != null) json.put("rtr", replyToResponseID.combined());
        json.put("ch", channel);
        if (payload != null && !payload.isEmpty()) json.put("pl", payload);
        json.put("fw", forward);
        json.put("or", origin);
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
        return replyToResponseID != null;
    }

    public IDProvider.ID getResponseID() {
        return responseID;
    }

    public IDProvider.ID getReplyToResponseID() {
        return replyToResponseID;
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

    public Packet modifyPayload(Consumer<JSONObject> payloadModifier) {
        payloadModifier.accept(payload);
        return this;
    }
}
