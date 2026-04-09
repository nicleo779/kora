package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;

public abstract class AbstractMapper<T> {
    protected final EntityTable<T> entityTable;
    protected final Class<T> entityClass;
    protected final SqlSession sqlSession;

    protected AbstractMapper(SqlSession sqlSession, EntityTable<T> entityTable) {
        this.sqlSession = sqlSession;
        this.entityTable = entityTable;
        this.entityClass = entityTable.entityType();
    }
}
