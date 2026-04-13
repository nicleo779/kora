package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

final class FunctionExpression implements SqlExpression {
    private final FunctionRenderer renderer;

    FunctionExpression(FunctionRenderer renderer) {
        this.renderer = renderer;
    }

    @Override
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        renderer.appendTo(sql, args, dbType);
    }

    @FunctionalInterface
    interface FunctionRenderer {
        void appendTo(StringBuilder sql, List<Object> args, DbType dbType);
    }
}
