package dev.heliosares.sync.net;

import dev.heliosares.sync.utils.UnmodifiableJSONObject;

public class UnmodifiablePacket extends Packet {

    public UnmodifiablePacket(Packet packet) {
        super(packet.getChannel(), packet.getPacketId(), packet.getPayload() == null ? null : new UnmodifiableJSONObject(packet.getPayload().toString()));
        super.setBlob(packet.getBlob());
        super.setForward(packet.getForward());
    }

    @Override
    public UnmodifiablePacket setBlob(byte[] blob) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnmodifiablePacket setForward(String forward) {
        throw new UnsupportedOperationException();
    }

}
