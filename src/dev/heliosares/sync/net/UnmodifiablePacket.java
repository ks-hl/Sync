package dev.heliosares.sync.net;

import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;

public class UnmodifiablePacket extends Packet {

    public UnmodifiablePacket(Packet packet) {
        super(packet.getChannel(), packet.getPacketId(),
                packet.getPayload() == null ? null : new JSONObject(packet.getPayload().toString()) {
                    private boolean lock;

                    @Override
                    public JSONObject accumulate(String key, Object value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject append(String key, Object value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.append(key, value);
                    }

                    @Override
                    public JSONObject increment(String key) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.increment(key);
                    }

                    @Override
                    public Object remove(String key) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.remove(key);
                    }

                    @Override
                    public JSONObject put(String key, boolean value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject put(String key, Collection<?> value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject put(String key, double value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject put(String key, float value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject put(String key, int value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject put(String key, long value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject put(String key, Map<?, ?> value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject put(String key, Object value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.put(key, value);
                    }

                    @Override
                    public JSONObject putOnce(String key, Object value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.putOnce(key, value);
                    }

                    @Override
                    public JSONObject putOpt(String key, Object value) {
                        if (lock)
                            throw new UnsupportedOperationException();
                        return super.putOpt(key, value);
                    }

                    public JSONObject lock() {
                        this.lock = true;
                        return this;
                    }
                }.lock(), packet.getBlob());
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
