package dev.heliosares.sync.params.mapper;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;

public abstract class CollectionMapper<T, C extends Collection<T>> extends JSONMapper<C> {
    @Nonnull
    private final JSONMapper<T> mapper;

    public CollectionMapper(@NotNull JSONMapper<T> mapper) {
        this.mapper = mapper;
    }

    @Override
    public JSONArray mapToJSON(C collection) {
        JSONArray out = new JSONArray();
        collection.forEach(element -> out.put(unNull.apply(mapper.mapToJSON(element))));
        return out;
    }

    @Override
    public C mapFromJSON(Object o) {
        C out = create();
        JSONArray arr = (JSONArray) o;
        arr.forEach(element -> out.add(mapper.mapFromJSON(reNull.apply(element))));
        return out;
    }

    protected abstract C create();

    public static class HashSetMapper<T> extends CollectionMapper<T, HashSet<T>> {

        public HashSetMapper(@NotNull JSONMapper<T> mapper) {
            super(mapper);
        }

        protected final HashSet<T> create() {
            return new HashSet<>();
        }
    }


    public static class ArrayListMapper<T> extends CollectionMapper<T, ArrayList<T>> {

        public ArrayListMapper(@NotNull JSONMapper<T> mapper) {
            super(mapper);
        }

        protected final ArrayList<T> create() {
            return new ArrayList<>();
        }
    }
}
