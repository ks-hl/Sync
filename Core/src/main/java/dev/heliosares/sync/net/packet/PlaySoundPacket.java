package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.params.param.JSONParam;
import dev.heliosares.sync.params.mapper.JSONMappers;
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
        if (!isResponse()) sound().requireNonnull();
    }

    public JSONParam<String> to() {
        return new JSONParam<>(getPayload(), "to", JSONMappers.STRING);
    }

    public JSONParam<String> sound() {
        return new JSONParam<>(getPayload(), "sound", JSONMappers.STRING);
    }

    public JSONParam<Double> pitch() {
        return new JSONParam<>(getPayload(), "pitch", JSONMappers.DOUBLE);
    }

    public JSONParam<Double> volume() {
        return new JSONParam<>(getPayload(), "volume", JSONMappers.DOUBLE);
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public PlaySoundPacket createResponse(JSONObject payload) {
        return (PlaySoundPacket) super.createResponse(payload);
    }
}
