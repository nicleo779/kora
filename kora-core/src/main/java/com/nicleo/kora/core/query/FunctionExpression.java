package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;

final class FunctionExpression implements SqlExpression {
    private final FunctionRenderer renderer;

    FunctionExpression(FunctionRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void appendTo(RenderContext context) {
        renderer.appendTo(context);
    }

    @FunctionalInterface
    interface FunctionRenderer {
        void appendTo(RenderContext context);
    }
}
