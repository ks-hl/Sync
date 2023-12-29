package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

import java.util.Base64;

public class BlobPacket extends Packet {
    private byte[] blob;

    public BlobPacket(String channel, JSONObject payload) {
        super(channel, PacketType.API_WITH_BLOB, payload, null, false);
    }

    public BlobPacket(JSONObject json) {
        super(json);
    }

    public byte[] getBlob() {
        return blob;
    }

    public Packet setBlob(byte[] blob) {
        if (this.getType() != PacketType.API_WITH_BLOB)
            throw new IllegalArgumentException("Can not set a blob for a packet other than type BLOB");
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
            String encoded = Base64.getEncoder().encodeToString(blob);
            if (encoded.length() > 255) encoded = encoded.substring(0, 255) + "...";
            line += encoded;
        }
        return line;
    }
}
