package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;
import com.nicleo.kora.core.runtime.dialect.SqlDialects;

final class AliasReferenceExpression implements SqlExpression {
    private final String alias;

    AliasReferenceExpression(String alias) {
        this.alias = alias;
    }

    @Override
    public void appendTo(RenderContext context) {
        context.sql().append(SqlDialects.identifiers(context.dialect().dbType()).quote(alias));
    }
}
