package dev.heliosares.sync.utils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CompletableException<T extends Throwable> extends CompletableFuture<T> {
    public void getAndThrow() throws ExecutionException, InterruptedException, T {
        handleThrowable(get());
    }

    public void getAndThrow(long time, TimeUnit timeUnit) throws ExecutionException, InterruptedException, T, TimeoutException {
        handleThrowable(get(time, timeUnit));
    }

    private void handleThrowable(T t) throws T {
        if (t == null) return;
        throw t;
    }
}
