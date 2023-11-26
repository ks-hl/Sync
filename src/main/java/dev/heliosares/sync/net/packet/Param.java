package dev.heliosares.sync.net.packet;

import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.Objects;

public abstract class Param<T> {
    private final JSONObject handle;
    private final String key;

    Param(JSONObject handle, String key) {
        this.handle = handle;
        this.key = key;
    }

    @Nullable
    public T get() {
        if (handle.has(key)) return map(handle.get(key));
        return null;
    }

    public T get(T def) {
        T out = get();
        return out == null ? def : out;
    }

    public void set(T value) {
        handle.put(key, value);
    }

    protected abstract T map(Object o);

    public void requireNonnull() {
        if (get() != null) return;
        throw new MalformedPacketException("No value for " + key);
    }

    public static class StringParam extends Param<String> {
        StringParam(JSONObject handle, String key) {
            super(handle, key);
        }

        @Override
        protected String map(Object o) {
            return Objects.toString(o);
        }
    }

    public static class IntParam extends Param<Integer> {
        IntParam(JSONObject handle, String key) {
            super(handle, key);
        }

        @Override
        protected Integer map(Object o) {
            return (Integer) o;
        }
    }

    public static class DoubleParam extends Param<Double> {
        DoubleParam(JSONObject handle, String key) {
            super(handle, key);
        }

        @Override
        protected Double map(Object o) {
            return (Double) o;
        }
    }

    public static class BooleanParam extends Param<Boolean> {
        BooleanParam(JSONObject handle, String key) {
            super(handle, key);
        }

        @Override
        protected Boolean map(Object o) {
            return (Boolean) o;
        }
    }

    public static class LongParam extends Param<Long> {
        LongParam(JSONObject handle, String key) {
            super(handle, key);
        }

        @Override
        protected Long map(Object o) {
            return (Long) o;
        }
    }
}
