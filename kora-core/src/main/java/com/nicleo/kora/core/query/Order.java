package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;

public record Order(SqlExpression expression, boolean ascending) {
    public void appendTo(RenderContext context) {
        expression.appendTo(context);
        context.sql().append(ascending ? " ASC" : " DESC");
    }
}
