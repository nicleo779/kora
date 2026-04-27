package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.SqlRequest;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class RenderContext {
    private final SqlDialect dialect;
    private final StringBuilder sql;
    private final List<Object> args;
    private final Map<EntityTable<?>, String> columnQualifiers;

    public RenderContext(SqlDialect dialect) {
        this(dialect, new StringBuilder(), new ArrayList<>(), new IdentityHashMap<>());
    }

    public RenderContext(SqlDialect dialect, StringBuilder sql, List<Object> args) {
        this(dialect, sql, args, new IdentityHashMap<>());
    }

    public RenderContext(SqlDialect dialect, StringBuilder sql, List<Object> args, Map<EntityTable<?>, String> columnQualifiers) {
        this.dialect = dialect;
        this.sql = sql;
        this.args = args;
        this.columnQualifiers = columnQualifiers;
    }

    public static RenderContext bridge(DbType dbType, StringBuilder sql, List<Object> args) {
        return new RenderContext(SqlDialects.dialect(dbType), sql, args);
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public StringBuilder sql() {
        return sql;
    }

    public List<Object> args() {
        return args;
    }

    public void clearColumnQualifiers() {
        columnQualifiers.clear();
    }

    public void qualify(EntityTable<?> table, String qualifier) {
        if (qualifier == null || qualifier.isBlank()) {
            columnQualifiers.remove(table);
            return;
        }
        columnQualifiers.put(table, qualifier);
    }

    public String columnQualifier(EntityTable<?> table) {
        return columnQualifiers.get(table);
    }

    public SqlRequest toRequest() {
        return new SqlRequest(sql.toString(), args.toArray());
    }
}
