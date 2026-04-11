package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlSession;

public final class Wrapper {
    private Wrapper() {
    }

    public static QueryWrapper query() {
        return new QueryWrapper();
    }

    public static QueryWrapper query(SqlSession sqlSession) {
        return new QueryWrapper(sqlSession);
    }

    public static QueryWrapper query(QueryWrapper.SqlSessionHandleProvider sqlSessionHandleProvider) {
        return new QueryWrapper(sqlSessionHandleProvider);
    }

    public static WhereWrapper where() {
        return new WhereWrapper();
    }

    public static UpdateWrapper update() {
        return new UpdateWrapper();
    }
}
