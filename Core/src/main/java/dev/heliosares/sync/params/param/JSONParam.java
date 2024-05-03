package dev.heliosares.sync.params.param;

import dev.heliosares.sync.net.packet.MalformedPacketException;
import dev.heliosares.sync.params.mapper.JSONMapper;
import org.json.JSONObject;

import javax.annotation.Nullable;
import java.util.Optional;

public class JSONParam<T> {
    private final JSONObject handle;
    private final String key;
    private final JSONMapper<T> mapper;

    public JSONParam(JSONObject handle, String key, JSONMapper<T> mapper) {
        this.handle = handle;
        this.key = key;
        this.mapper = mapper;
    }

    @Nullable
    public T get() {
        if (handle.has(key)) return mapper.mapFromJSON(handle.get(key));
        return null;
    }

    public T get(T def) {
        T out = get();
        return out == null ? def : out;
    }

    public Optional<T> opt() {
        return Optional.ofNullable(get());
    }

    public void set(T value) {
        handle.put(key, mapper.mapToJSON(value));
    }

    public void requireNonnull() {
        if (get() != null) return;
        throw new MalformedPacketException("No value for " + key);
    }
}
