package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;

public interface IdGenerator {
    Object generate(SqlExecutor sqlExecutor, EntityTable<?> entityTable, Object entity);
}
