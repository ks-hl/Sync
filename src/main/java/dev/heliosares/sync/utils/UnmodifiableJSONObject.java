package dev.heliosares.sync.utils;

import org.json.JSONObject;

import java.util.Collection;
import java.util.Map;

public class UnmodifiableJSONObject extends JSONObject {
    private final boolean lock;

    public UnmodifiableJSONObject(String string) {
        super(string);
        lock = true;
    }

    @Override
    public JSONObject accumulate(String key, Object value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject append(String key, Object value) {
        checkLock();
        return super.append(key, value);
    }

    @Override
    public JSONObject increment(String key) {
        checkLock();
        return super.increment(key);
    }

    @Override
    public Object remove(String key) {
        checkLock();
        return super.remove(key);
    }

    @Override
    public JSONObject put(String key, boolean value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject put(String key, Collection<?> value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject put(String key, double value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject put(String key, float value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject put(String key, int value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject put(String key, long value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject put(String key, Map<?, ?> value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject put(String key, Object value) {
        checkLock();
        return super.put(key, value);
    }

    @Override
    public JSONObject putOnce(String key, Object value) {
        checkLock();
        return super.putOnce(key, value);
    }

    @Override
    public JSONObject putOpt(String key, Object value) {
        checkLock();
        return super.putOpt(key, value);
    }

    private void checkLock() {
        if (lock) throw new UnsupportedOperationException("This JSONObject is locked");
    }
}
