package dev.heliosares.sync.net;

import dev.heliosares.sync.utils.UnmodifiableJSONObject;

public class UnmodifiablePacket extends Packet {

    public UnmodifiablePacket(Packet packet) {
        super(packet.getChannel(), packet.getPacketId(), packet.getPayload() == null ? null :
                new UnmodifiableJSONObject(packet.getPayload().toString()), packet.getResponseID(), packet.isResponse());
        super.setBlob(packet.getBlob());
        super.setForward(packet.getForward());
        super.setOrigin(packet.getOrigin());
    }

    @Override
    public UnmodifiablePacket setBlob(byte[] blob) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnmodifiablePacket setForward(String forward) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setOrigin(String forward) {
        throw new UnsupportedOperationException();
    }

}
