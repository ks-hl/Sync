package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import dev.heliosares.sync.params.param.JSONParam;
import dev.heliosares.sync.params.mapper.JSONMappers;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.util.Objects;

public class CommandPacket extends Packet {

    public CommandPacket(@Nonnull String command) {
        super(null, PacketType.COMMAND, new JSONObject());
        command().set(Objects.requireNonNull(command));
    }

    public CommandPacket(JSONObject json) {
        super(json);
        if (!isResponse()) {
            command().requireNonnull();
        }
    }

    public JSONParam<String> command() {
        return new JSONParam<>(getPayload(), "command", JSONMappers.STRING);
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public CommandPacket createResponse(JSONObject payload) {
        return (CommandPacket) super.createResponse(payload);
    }
}
