package com.nicleo.kora.core.query;

public final class Wrapper {
    private Wrapper() {
    }

    public static <T> QueryWrapper<T> of(Class<T> entityType) {
        return new QueryWrapper<>(entityType);
    }
}
