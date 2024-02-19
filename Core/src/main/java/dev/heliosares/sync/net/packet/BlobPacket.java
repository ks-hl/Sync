package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

import java.util.Base64;

public class BlobPacket extends Packet {
    private byte[] blob;

    public BlobPacket(String channel, PacketType type, JSONObject payload) {
        super(channel, type, payload, null, false);
    }

    public BlobPacket(String channel, JSONObject payload) {
        this(channel, PacketType.API_WITH_BLOB, payload);
    }

    public BlobPacket(JSONObject json) {
        super(json);
    }

    public byte[] getBlob() {
        return blob;
    }

    public Packet setBlob(byte[] blob) {
        this.blob = blob;
        return this;
    }

    /**
     * @see Packet#createResponse(JSONObject)
     */
    @Override
    public BlobPacket createResponse(JSONObject payload) {
        return (BlobPacket) super.createResponse(payload);
    }

    @Override
    public String toString() {
        String line = super.toString() + ", blob";
        if (blob == null) {
            line += "=null";
        } else {
            line += "[" + blob.length + "]=";
            if (this.getType() == PacketType.P2P_AUTH) {
                line += "REDACTED";
            } else {
                String encoded = Base64.getEncoder().encodeToString(blob);
                if (encoded.length() > 255) encoded = encoded.substring(0, 255) + "...";
                line += encoded;
            }
        }
        return line;
    }
}
