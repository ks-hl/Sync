package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.params.param.JSONParam;
import dev.heliosares.sync.params.mapper.JSONMappers;
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

    public JSONParam<String> player() {
        return new JSONParam<>(getPayload(), "player", JSONMappers.STRING);
    }

    public JSONParam<String> node() {
        return new JSONParam<>(getPayload(), "node", JSONMappers.STRING);
    }

    public JSONParam<Boolean> result() {
        return new JSONParam<>(getPayload(), "result", JSONMappers.BOOLEAN);
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public HasPermissionPacket createResponse(JSONObject payload) {
        return (HasPermissionPacket) super.createResponse(payload);
    }
}
