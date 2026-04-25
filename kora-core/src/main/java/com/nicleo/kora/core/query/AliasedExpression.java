package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;
import com.nicleo.kora.core.runtime.dialect.SqlDialects;

record AliasedExpression(SqlExpression expression, String alias) implements NamedSqlExpression {
    @Override
    public SqlExpression source() {
        return expression;
    }

    @Override
    public void appendTo(RenderContext context) {
        expression.appendTo(context);
        context.sql().append(" AS ").append(SqlDialects.identifiers(context.dialect().dbType()).quote(alias));
    }
}
