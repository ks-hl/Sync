package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;
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

    public MessagePacket(@Nullable String node, @Nullable UUID toUUID, @Nullable String toUsername, BaseComponent[] msg, boolean otherServersOnly) {
        this(node, toUUID, toUsername, null, ComponentSerializer.toString(msg).replace("[JSON]", ""), otherServersOnly);
    }

    public MessagePacket(JSONObject json) {
        super(json);
    }

    public Param.StringParam to() {
        return new Param.StringParam(getPayload(), "to");
    }

    public Param.StringParam node() {
        return new Param.StringParam(getPayload(), "node");
    }

    public Param.StringParam msg() {
        return new Param.StringParam(getPayload(), "msg");
    }

    public Param.StringParam json() {
        return new Param.StringParam(getPayload(), "json");
    }

    public Param.BooleanParam otherServersOnly() {
        return new Param.BooleanParam(getPayload(), "others_only");
    }
}
