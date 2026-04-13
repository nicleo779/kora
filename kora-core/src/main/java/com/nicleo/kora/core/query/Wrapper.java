package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlExecutor;

public final class Wrapper {
    private Wrapper() {
    }

    public static QueryWrapper query() {
        return new QueryWrapper();
    }

    public static QueryWrapper query(SqlExecutor sqlExecutor) {
        return new QueryWrapper(sqlExecutor);
    }

    public static WhereWrapper where() {
        return new WhereWrapper();
    }

    public static UpdateWrapper update() {
        return new UpdateWrapper();
    }
}
