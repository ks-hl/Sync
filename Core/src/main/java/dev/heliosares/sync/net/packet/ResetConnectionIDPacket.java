package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.params.param.JSONParam;
import dev.heliosares.sync.params.mapper.JSONMappers;
import org.json.JSONObject;

public class ResetConnectionIDPacket extends Packet {

    public ResetConnectionIDPacket(short connectionID) {
        super(null, PacketType.RESET_CONNECTION_ID, new JSONObject());

        id().set(connectionID);
    }

    public ResetConnectionIDPacket(JSONObject json) {
        super(json);
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public ResetConnectionIDPacket createResponse(JSONObject payload) {
        return (ResetConnectionIDPacket) super.createResponse(payload);
    }

    public ResetConnectionIDPacket createResponse() {
        return createResponse(new JSONObject());
    }

    public JSONParam<Short> id() {
        return new JSONParam<>(getPayload(), "id", JSONMappers.SHORT);
    }
}
