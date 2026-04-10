package com.nicleo.kora.core.query;

public final class Wrapper {
    private Wrapper() {
    }

    public static <T> QueryWrapper<T> query() {
        return new QueryWrapper<>();
    }

    public static <T> WhereWrapper<T> where() {
        return new WhereWrapper<>();
    }

    public static <T> UpdateWrapper<T> update() {
        return new UpdateWrapper<>();
    }
}
