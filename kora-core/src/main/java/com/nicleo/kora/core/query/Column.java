package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;
import com.nicleo.kora.core.runtime.dialect.SqlDialects;

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

    public String expression(RenderContext context) {
        var dbType = context.dialect().dbType();
        String qualifier = context == null ? table.qualifier() : context.columnQualifier(table);
        return SqlDialects.identifiers(dbType).columnReference(qualifier, columnName);
    }

    @Override
    public void appendTo(RenderContext context) {
        context.sql().append(expression(context));
    }
}
