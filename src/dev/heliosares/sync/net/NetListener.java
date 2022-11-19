package dev.heliosares.sync.net;

public abstract class NetListener {

    private final int packetid;
    private final String channel;

    public NetListener(int packetid, String channel) {
        this.packetid = packetid;
        this.channel = channel;
        if (channel != null && !channel.matches("\\w+:\\w+")) {
            throw new IllegalArgumentException("Channel name must conform to 'PluginName:Channel'");
        }
    }

    public abstract void execute(String server, Packet packet);

    public final int getPacketId() {
        return packetid;
    }

    public final String getChannel() {
        return channel;
    }
}
