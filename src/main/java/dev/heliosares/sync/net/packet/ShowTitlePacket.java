package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.UUID;

public class ShowTitlePacket extends Packet {

    public ShowTitlePacket(@Nullable String title, @Nullable String subtitle, int fadeIn, int duration, int fadeOut, @Nullable UUID toUUID, @Nullable String toUsername) {
        super(null, PacketType.COMMAND, new JSONObject());
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

    public Param.StringParam to() {
        return new Param.StringParam(getPayload(), "to");
    }

    public Param.StringParam title() {
        return new Param.StringParam(getPayload(), "title");
    }

    public Param.StringParam subtitle() {
        return new Param.StringParam(getPayload(), "subtitle");
    }

    public Param.IntParam fadeIn() {
        return new Param.IntParam(getPayload(), "fadein");
    }

    public Param.IntParam duration() {
        return new Param.IntParam(getPayload(), "duration");
    }

    public Param.IntParam fadeOut() {
        return new Param.IntParam(getPayload(), "fadeout");
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public ShowTitlePacket createResponse(JSONObject payload) {
        return (ShowTitlePacket) super.createResponse(payload);
    }
}
