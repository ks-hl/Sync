package dev.heliosares.sync.params.mapper;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.function.Function;

public abstract class JSONMapper<T> {

    protected static final Function<Object, Object> unNull = o -> o == null ? JSONObject.NULL : o;
    protected static final Function<Object, Object> reNull = o -> o.equals(JSONObject.NULL) ? null : o;

    /**
     * Maps the generic value directly from JSON to the specific value held by the wrapper.
     */
    public abstract T mapFromJSON(@Nullable Object o);

    /**
     * Maps the specific value to a generic value before being assigned to JSON
     */
    public abstract @Nullable Object mapToJSON(T t);


    public static class HashMapMapper<V> extends MapMapper<V, HashMap<String, V>> {

        public HashMapMapper(@NotNull JSONMapper<V> mapper) {
            super(mapper);
        }

        protected final HashMap<String, V> create() {
            return new HashMap<>();
        }
    }
}
