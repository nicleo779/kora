package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

record AliasedExpression(SqlExpression expression, String alias) implements SqlExpression {
    @Override
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        expression.appendTo(sql, args, dbType);
        sql.append(" AS ").append(alias);
    }
}
