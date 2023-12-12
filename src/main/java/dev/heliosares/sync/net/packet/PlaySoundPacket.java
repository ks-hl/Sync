package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public class PlaySoundPacket extends Packet {

    public PlaySoundPacket(String sound, float pitch, float volume, @Nullable UUID toUUID, @Nullable String toUsername) {
        super(null, PacketType.PLAY_SOUND, new JSONObject());
        sound().set(Objects.requireNonNull(sound));

        if (pitch > 1E-6) pitch().set((double) pitch);
        if (volume > 1E-6) volume().set((double) volume);
        if (toUUID != null) to().set(toUUID.toString());
        else if (toUsername != null) to().set(toUsername);
    }

    public PlaySoundPacket(JSONObject json) {
        super(json);
        if(!isResponse()) sound().requireNonnull();
    }

    public Param.StringParam to() {
        return new Param.StringParam(getPayload(), "to");
    }

    public Param.StringParam sound() {
        return new Param.StringParam(getPayload(), "sound");
    }

    public Param.DoubleParam pitch() {
        return new Param.DoubleParam(getPayload(), "pitch");
    }

    public Param.DoubleParam volume() {
        return new Param.DoubleParam(getPayload(), "volume");
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public PlaySoundPacket createResponse(JSONObject payload) {
        return (PlaySoundPacket) super.createResponse(payload);
    }
}
