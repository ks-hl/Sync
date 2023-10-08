package dev.heliosares.sync.utils;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class ConcurrentMap<K, V> extends ConcurrentReference<Map<K, V>> {

    public ConcurrentMap() {
        super(new HashMap<>());
    }

    @CheckReturnValue
    @Nullable
    public V get(K key) {
        return function(map -> map.get(key));
    }

    @CheckReturnValue
    @Nullable
    public V get(Predicate<V> predicate) {
        return function(map -> map.values().stream().filter(predicate).findFirst().orElse(null));
    }

    public void forEach(BiConsumer<K, V> consumer) {
        consume(map -> map.forEach(consumer));
    }

    @Override
    public int hashCode() {
        return function(players -> players.values().stream().mapToInt(Object::hashCode).reduce(0, (a, b) -> a ^ b));
    }
}
