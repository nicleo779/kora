package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

record LiteralExpression(Object value) implements SqlExpression {
    @Override
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        sql.append('?');
        args.add(value);
    }
}
