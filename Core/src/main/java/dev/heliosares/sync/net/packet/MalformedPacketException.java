package dev.heliosares.sync.net.packet;

public class MalformedPacketException extends IllegalArgumentException {
    public MalformedPacketException(String message) {
        super(message);
    }
}
