package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.params.param.JSONParam;
import dev.heliosares.sync.params.mapper.JSONMappers;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class MessagePacket extends Packet {

    public MessagePacket(@Nullable String node, @Nullable UUID toUUID, @Nullable String toUsername, @Nullable String msg, @Nullable String json, boolean otherServersOnly) {
        super(null, PacketType.MESSAGE, new JSONObject());
        if (toUUID != null) to().set(toUUID.toString());
        else if (toUsername != null) to().set(toUsername);
        node().set(node);
        msg().set(msg);
        json().set(json);
        otherServersOnly().set(otherServersOnly);
    }

    public MessagePacket(JSONObject json) {
        super(json);
    }

    public JSONParam<String> to() {
        return new JSONParam<String>(getPayload(), "to", JSONMappers.STRING);
    }

    public JSONParam<String> node() {
        return new JSONParam<String>(getPayload(), "node", JSONMappers.STRING);
    }

    public JSONParam<String> msg() {
        return new JSONParam<String>(getPayload(), "msg", JSONMappers.STRING);
    }

    public JSONParam<String> json() {
        return new JSONParam<String>(getPayload(), "json", JSONMappers.STRING);
    }

    public JSONParam<Boolean> otherServersOnly() {
        return new JSONParam<Boolean>(getPayload(), "others_only", JSONMappers.BOOLEAN);
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public MessagePacket createResponse(JSONObject payload) {
        return (MessagePacket) super.createResponse(payload);
    }
}
