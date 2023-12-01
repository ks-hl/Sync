package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class HasPermissionPacket extends Packet {

    public HasPermissionPacket(@Nullable UUID uuid, @Nullable String username, String node) {
        super(null, PacketType.HAS_PERMISSION, new JSONObject());

        if (uuid != null) player().set(uuid.toString());
        else if (username != null) player().set(username);
        else throw new IllegalArgumentException("No UUID or username provided.");

        node().set(node);
    }

    public HasPermissionPacket(JSONObject json) {
        super(json);
    }

    public Param.StringParam player() {
        return new Param.StringParam(getPayload(), "player");
    }

    public Param.StringParam node() {
        return new Param.StringParam(getPayload(), "node");
    }

    public Param.BooleanParam result() {
        return new Param.BooleanParam(getPayload(), "result");
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public HasPermissionPacket createResponse(JSONObject payload) {
        return (HasPermissionPacket) super.createResponse(payload);
    }
}
