package dev.heliosares.sync.net;

import dev.heliosares.sync.net.packet.*;
import dev.heliosares.sync.net.packet.Packet;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.function.Function;

public enum PacketType {
    KEEP_ALIVE(1), //
    COMMAND(2, CommandPacket::new), //
    API(3), //
    API_WITH_BLOB(4, BlobPacket::new), //
    SERVER_LIST(5), //
    PLAYER_DATA(6),//
    MESSAGE(7, MessagePacket::new),//
    PLAY_SOUND(8, PlaySoundPacket::new),//
    SHOW_TITLE(9, ShowTitlePacket::new);//

    public final int id;
    public final Function<JSONObject, Packet> mapper;

    PacketType(int id) {
        this(id, Packet::new);
    }

    PacketType(int id, Function<JSONObject, Packet> mapper) {
        this.id = id;
        this.mapper = mapper;
    }

    public static PacketType getByID(int id) {
        return Arrays.stream(values()).filter(type -> type.id == id).findAny().orElseThrow(() -> new IllegalArgumentException("Unknown PacketType, id=" + id));
    }

    public static Packet getPacketFromJSON(JSONObject o) {
        return getByID(o.getInt("typ")).mapper.apply(o);
    }
}
