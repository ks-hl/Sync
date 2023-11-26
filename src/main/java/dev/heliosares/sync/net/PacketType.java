package dev.heliosares.sync.net;

import dev.heliosares.sync.net.packet.BlobPacket;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.net.packet.MessagePacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.heliosares.sync.net.packet.PlaySoundPacket;
import dev.heliosares.sync.net.packet.ShowTitlePacket;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.function.Function;

public enum PacketType {

    // INTERNAL
    KEEP_ALIVE(1), //
    PING(2, PingPacket::new),//
    SERVER_LIST(3),//
    PLAYER_DATA(4),//


    // API
    API(5), //
    API_WITH_BLOB(6, BlobPacket::new), //


    // UTILITY
    COMMAND(7, CommandPacket::new), //
    MESSAGE(8, MessagePacket::new),//
    PLAY_SOUND(9, PlaySoundPacket::new),//
    SHOW_TITLE(10, ShowTitlePacket::new);//

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
