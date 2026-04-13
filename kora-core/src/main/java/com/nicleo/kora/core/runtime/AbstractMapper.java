package com.nicleo.kora.core.runtime;

public abstract class AbstractMapper<T> {
    protected final Class<T> entityClass;
    protected final SqlExecutor sqlExecutor;

    protected AbstractMapper(SqlExecutor sqlExecutor, Class<T> entityClass) {
        this.sqlExecutor = sqlExecutor;
        this.entityClass = entityClass;
    }
}
