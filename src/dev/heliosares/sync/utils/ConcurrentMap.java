package dev.heliosares.sync.utils;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ConcurrentMap<K, V> {
    private final Map<K, V> map;
    private final ReentrantLock lock = new ReentrantLock();

    public ConcurrentMap() {
        this(new HashMap<>());
    }

    public ConcurrentMap(Map<K, V> map) {
        this.map = map;
    }

    public void consume(Consumer<Map<K, V>> consumer, long waitLimit) {
        //noinspection ResultOfMethodCallIgnored
        function(map -> {
            consumer.accept(map);
            return null;
        }, waitLimit);
    }

    public void consume(Consumer<Map<K, V>> consumer) {
        consume(consumer, 5000L);
    }

    @CheckReturnValue
    public <T> T function(Function<Map<K, V>, T> function, long waitLimit) {
        try {
            if (!lock.tryLock(waitLimit, TimeUnit.MILLISECONDS)) return null;
        } catch (InterruptedException e) {
            return null;
        }
        try {
            return function.apply(map);
        } finally {
            lock.unlock();
        }
    }

    @CheckReturnValue
    public <T> T function(Function<Map<K, V>, T> function) {
        return function(function, 5000L);
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
