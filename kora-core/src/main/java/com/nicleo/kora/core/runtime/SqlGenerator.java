package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.QueryDefinition;
import com.nicleo.kora.core.query.UpdateDefinition;
import com.nicleo.kora.core.query.WhereDefinition;

import java.util.List;

public interface SqlGenerator {
    SqlRequest renderQuery(QueryDefinition definition, DbType dbType);

    SqlRequest renderSelect(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType);

    SqlRequest renderDelete(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType);

    SqlRequest renderUpdate(EntityTable<?> table, UpdateDefinition updateDefinition, DbType dbType);

    default SqlRequest renderInsert(EntityTable<?> table, List<String> columns, List<Object> args, DbType dbType) {
        throw new SqlSessionException("Insert render is not supported by generator: " + getClass().getName());
    }

    default SqlRequest rewriteCount(QueryDefinition definition, DbType dbType) {
        throw new SqlSessionException("Count rewrite is not supported by generator: " + getClass().getName());
    }
}
