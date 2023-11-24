package dev.heliosares.sync.net;

@Deprecated
public enum Packets {
    KEEPALIVE(PacketType.KEEP_ALIVE.id), //
    COMMAND(PacketType.COMMAND.id), //
    API(PacketType.API.id), //
    BLOB(PacketType.API_WITH_BLOB.id), //
    SERVER_LIST(PacketType.SERVER_LIST.id), //
    PLAYER_DATA(PacketType.PLAYER_DATA.id),//
    MESSAGE(PacketType.MESSAGE.id),//
    PLAY_SOUND(PacketType.PLAY_SOUND.id),//
    TITLE(PacketType.SHOW_TITLE.id);//

    public final int id;

    Packets(int id) {
        this.id = id;
    }
}
