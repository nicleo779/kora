package com.nicleo.kora.core.query;

public final class Wrapper {
    private Wrapper() {
    }

    public static QueryWrapper query() {
        return new QueryWrapper();
    }

    public static WhereWrapper where() {
        return new WhereWrapper();
    }

    public static UpdateWrapper update() {
        return new UpdateWrapper();
    }
}
