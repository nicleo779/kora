package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;

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
    public void appendTo(RenderContext context) {
        left.appendTo(context);
        context.sql().append(' ').append(operator).append(' ');
        right.appendTo(context);
    }
}
