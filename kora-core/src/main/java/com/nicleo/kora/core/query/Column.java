package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

public final class Column<T, V> implements SqlExpression {
    private final EntityTable<T> table;
    private final String columnName;
    private final Class<V> javaType;

    Column(EntityTable<T> table, String columnName, Class<V> javaType) {
        this.table = table;
        this.columnName = columnName;
        this.javaType = javaType;
    }

    public EntityTable<T> table() {
        return table;
    }

    public String columnName() {
        return columnName;
    }

    public Class<V> javaType() {
        return javaType;
    }

    public String expression() {
        return table.qualifier() + "." + columnName;
    }

    @Override
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        sql.append(expression());
    }
}
