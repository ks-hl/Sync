package dev.heliosares.sync.net;

import dev.heliosares.sync.net.packet.BlobPacket;
import dev.heliosares.sync.net.packet.CommandPacket;
import dev.heliosares.sync.net.packet.HasPermissionPacket;
import dev.heliosares.sync.net.packet.MalformedPacketException;
import dev.heliosares.sync.net.packet.MessagePacket;
import dev.heliosares.sync.net.packet.Packet;
import dev.heliosares.sync.net.packet.PingPacket;
import dev.heliosares.sync.net.packet.PlaySoundPacket;
import dev.heliosares.sync.net.packet.ResetConnectionIDPacket;
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
    P2P_AUTH(5, BlobPacket::new),//
    RESET_CONNECTION_ID(6, ResetConnectionIDPacket::new), //


    // API
    API(30), //
    API_WITH_BLOB(31, BlobPacket::new), //


    // UTILITY
    COMMAND(60, CommandPacket::new), //
    MESSAGE(61, MessagePacket::new),//
    PLAY_SOUND(62, PlaySoundPacket::new),//
    SHOW_TITLE(63, ShowTitlePacket::new),//
    HAS_PERMISSION(64, HasPermissionPacket::new);//

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
        if (!o.has("ty")) throw new MalformedPacketException("No type specified");
        return getByID(o.getInt("ty")).mapper.apply(o);
    }

    public boolean hasBlob() {
        return this == API_WITH_BLOB || this == P2P_AUTH;
    }
}
