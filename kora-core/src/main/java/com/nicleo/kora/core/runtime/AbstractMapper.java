package com.nicleo.kora.core.runtime;

public abstract class AbstractMapper<T> {
    protected final Class<?> entityClass;
    protected final SqlSession sqlSession;

    protected AbstractMapper(SqlSession sqlSession, Class<?> entityClass) {
        this.sqlSession = sqlSession;
        this.entityClass = entityClass;
    }
}
