package dev.heliosares.sync.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class CompletableException<T extends Throwable> extends CompletableFuture<T> {
    public void getAndThrow() throws ExecutionException, InterruptedException, T {
        T t = get();
        if (t == null) return;
        throw t;
    }
}
