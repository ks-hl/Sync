package dev.heliosares.sync.net;

@Deprecated
public enum Packets {
    @Deprecated KEEPALIVE(PacketType.KEEP_ALIVE.id), //
    @Deprecated COMMAND(PacketType.COMMAND.id), //
    @Deprecated API(PacketType.API.id), //
    @Deprecated BLOB(PacketType.API_WITH_BLOB.id), //
    @Deprecated SERVER_LIST(PacketType.SERVER_LIST.id), //
    @Deprecated PLAYER_DATA(PacketType.PLAYER_DATA.id),//
    @Deprecated MESSAGE(PacketType.MESSAGE.id),//
    @Deprecated PLAY_SOUND(PacketType.PLAY_SOUND.id),//
    @Deprecated TITLE(PacketType.SHOW_TITLE.id);//

    @Deprecated
    public final int id;

    Packets(int id) {
        this.id = id;
    }
}
