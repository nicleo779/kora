package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;

record LiteralExpression(Object value) implements SqlExpression {
    @Override
    public void appendTo(RenderContext context) {
        context.sql().append('?');
        context.args().add(value);
    }
}
