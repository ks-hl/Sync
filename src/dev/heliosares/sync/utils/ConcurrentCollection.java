package dev.heliosares.sync.utils;

import java.util.Collection;
import java.util.function.Consumer;

public class ConcurrentCollection<V> extends ConcurrentReference<Collection<V>> {

    public ConcurrentCollection(Collection<V> collection) {
        super(collection);
    }

    public void forEach(Consumer<V> consumer) {
        consume(collection -> collection.forEach(consumer));
    }

    @Override
    public int hashCode() {
        return function(collection -> collection.stream().mapToInt(Object::hashCode).reduce(0, (a, b) -> a ^ b));
    }
}
