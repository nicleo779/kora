package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

public record Order(SqlExpression expression, boolean ascending) {
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        expression.appendTo(sql, args, dbType);
        sql.append(ascending ? " ASC" : " DESC");
    }
}
