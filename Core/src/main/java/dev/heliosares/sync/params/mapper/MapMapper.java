package dev.heliosares.sync.params.mapper;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import java.util.Map;

public abstract class MapMapper<V, M extends Map<String, V>> extends JSONMapper<M> {
    @Nonnull
    private final JSONMapper<V> mapper;

    public MapMapper(@NotNull JSONMapper<V> mapper) {
        this.mapper = mapper;
    }

    @Override
    public JSONArray mapToJSON(M map) {
        JSONArray out = new JSONArray();
        map.forEach((k, v) -> out.put(new JSONObject().put(k, unNull.apply(mapper.mapToJSON(v)))));
        return out;
    }

    @Override
    public M mapFromJSON(Object o) {
        M out = create();
        ((JSONArray) o).forEach(element_ -> {
            if (!(element_ instanceof JSONObject element)) return;
            String key = element.keys().next();
            out.put(key, mapper.mapFromJSON(reNull.apply(element.get(key))));
        });
        return out;
    }

    protected abstract M create();
}
