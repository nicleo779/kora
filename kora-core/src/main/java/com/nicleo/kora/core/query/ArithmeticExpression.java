package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

final class ArithmeticExpression implements SqlExpression {
    private final SqlExpression left;
    private final String operator;
    private final SqlExpression right;

    ArithmeticExpression(SqlExpression left, String operator, SqlExpression right) {
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    @Override
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        left.appendTo(sql, args, dbType);
        sql.append(' ').append(operator).append(' ');
        right.appendTo(sql, args, dbType);
    }
}
