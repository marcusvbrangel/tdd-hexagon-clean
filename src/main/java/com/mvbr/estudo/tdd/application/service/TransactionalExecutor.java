package com.mvbr.estudo.tdd.application.service;

import java.util.function.Supplier;

public interface TransactionalExecutor {

    <T> T execute(Supplier<T> action);

    default void execute(Runnable action) {
        execute(() -> {
            action.run();
            return null;
        });
    }
}
