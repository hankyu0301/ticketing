package com.ticketing.infrastructure.lock.executor;

import java.util.function.Supplier;

public interface LockExecutor {
    <T> T execute(String lockName, Supplier<T> supplier);
}