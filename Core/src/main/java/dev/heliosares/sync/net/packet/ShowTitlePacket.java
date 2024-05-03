package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.params.param.JSONParam;
import dev.heliosares.sync.params.mapper.JSONMappers;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class ShowTitlePacket extends Packet {

    public ShowTitlePacket(@Nullable String title, @Nullable String subtitle, int fadeIn, int duration, int fadeOut, @Nullable UUID toUUID, @Nullable String toUsername) {
        super(null, PacketType.SHOW_TITLE, new JSONObject());
        title().set(title);
        subtitle().set(subtitle);
        if (fadeIn > 0) fadeIn().set(fadeIn);
        if (duration > 0) duration().set(duration);
        if (fadeOut > 0) fadeOut().set(fadeOut);
        if (toUUID != null) to().set(toUUID.toString());
        else if (toUsername != null) to().set(toUsername);
    }

    public ShowTitlePacket(JSONObject json) {
        super(json);
    }

    public JSONParam<String> to() {
        return new JSONParam<>(getPayload(), "to", JSONMappers.STRING);
    }

    public JSONParam<String> title() {
        return new JSONParam<>(getPayload(), "title", JSONMappers.STRING);
    }

    public JSONParam<String> subtitle() {
        return new JSONParam<>(getPayload(), "subtitle", JSONMappers.STRING);
    }

    public JSONParam<Integer> fadeIn() {
        return new JSONParam<>(getPayload(), "fadein", JSONMappers.INTEGER);
    }

    public JSONParam<Integer> duration() {
        return new JSONParam<>(getPayload(), "duration", JSONMappers.INTEGER);
    }

    public JSONParam<Integer> fadeOut() {
        return new JSONParam<>(getPayload(), "fadeout", JSONMappers.INTEGER);
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public ShowTitlePacket createResponse(JSONObject payload) {
        return (ShowTitlePacket) super.createResponse(payload);
    }
}
