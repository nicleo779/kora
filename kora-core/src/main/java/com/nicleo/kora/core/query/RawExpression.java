package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;

record RawExpression(String value) implements SqlExpression {
    @Override
    public void appendTo(RenderContext context) {
        context.sql().append(value);
    }
}
