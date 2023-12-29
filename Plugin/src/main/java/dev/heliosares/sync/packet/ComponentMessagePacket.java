package dev.heliosares.sync.packet;

import dev.heliosares.sync.net.packet.MessagePacket;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.chat.ComponentSerializer;

import javax.annotation.Nullable;
import java.util.UUID;

public class ComponentMessagePacket extends MessagePacket {
    public ComponentMessagePacket(@Nullable String node, @Nullable UUID toUUID, @Nullable String toUsername, BaseComponent[] msg, boolean otherServersOnly) {
        super(node, toUUID, toUsername, null, ComponentSerializer.toString(msg).replace("[JSON]", ""), otherServersOnly);
    }
}
