package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;

public abstract class AbstractMapper<T> {
    protected final EntityTable<T> entityTable;
    protected final Class<T> entityClass;
    protected final SqlExecutor sqlExecutor;

    protected AbstractMapper(SqlExecutor sqlExecutor, EntityTable<T> entityTable) {
        this.sqlExecutor = sqlExecutor;
        this.entityTable = entityTable;
        this.entityClass = entityTable.entityType();
    }
}
