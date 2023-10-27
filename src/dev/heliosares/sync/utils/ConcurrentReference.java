package dev.heliosares.sync.utils;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

public class ConcurrentReference<T> {
    @Nonnull
    private final T t;
    private final ReentrantLock lock = new ReentrantLock();

    public ConcurrentReference(@Nonnull T t) {
        //noinspection ConstantValue
        if (t == null) throw new NullPointerException();
        this.t = t;
    }

    public void consume(Consumer<T> consumer, long waitLimit) {
        //noinspection ResultOfMethodCallIgnored
        function(map -> {
            consumer.accept(map);
            return null;
        }, waitLimit);
    }

    public void consume(Consumer<T> consumer) {
        consume(consumer, 5000L);
    }

    @CheckReturnValue
    public <V> V function(Function<T, V> function, long waitLimit) {
        try {
            if (!lock.tryLock(waitLimit, TimeUnit.MILLISECONDS)) return null;
        } catch (InterruptedException e) {
            return null;
        }
        try {
            return function.apply(t);
        } finally {
            lock.unlock();
        }
    }

    @CheckReturnValue
    public <V> V function(Function<T, V> function) {
        return function(function, 5000L);
    }

    @Override
    public int hashCode() {
        return function(Object::hashCode);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConcurrentReference<?> concurrentReference)) return false;
        return function(t->t.equals(concurrentReference));
    }
}
