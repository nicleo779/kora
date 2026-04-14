package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.SqlDialects;

import java.util.List;

record AliasedExpression(SqlExpression expression, String alias) implements NamedSqlExpression {
    @Override
    public SqlExpression source() {
        return expression;
    }

    @Override
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        expression.appendTo(sql, args, dbType);
        sql.append(" AS ").append(SqlDialects.identifiers(dbType).quote(alias));
    }
}
