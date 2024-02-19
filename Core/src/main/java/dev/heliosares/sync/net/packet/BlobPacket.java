package dev.heliosares.sync.net.packet;

import dev.heliosares.sync.net.PacketType;
import org.json.JSONObject;

import java.util.HexFormat;

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
        return super.toString() + ", " + blobToString();
    }

    @Override
    public String toHumanString() {
        return super.toHumanString() + ", " + blobToString();
    }

    public String blobToString() {
        String line = "blob";
        if (blob == null) {
            line += "=null";
        } else {
            line += "[" + blob.length + "]=";
            if (this.getType() == PacketType.P2P_AUTH) {
                line += "REDACTED";
            } else {
                String encoded = HexFormat.of().formatHex(blob);
                if (encoded.length() > 512) encoded = encoded.substring(0, 512) + "...";
                line += encoded;
            }
        }
        return line;
    }
}
