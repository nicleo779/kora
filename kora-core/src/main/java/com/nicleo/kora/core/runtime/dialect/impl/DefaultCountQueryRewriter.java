package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.dialect.CountQueryRewriter;
import com.nicleo.kora.core.runtime.dialect.QueryModel;
import com.nicleo.kora.core.runtime.dialect.RenderContext;

public final class DefaultCountQueryRewriter implements CountQueryRewriter {
    @Override
    public SqlRequest rewrite(QueryModel queryModel, RenderContext context) {
        SqlRequest query = context.dialect().queryRenderer().render(queryModel, context);
        return new SqlRequest("select count(*) from (" + query.getSql() + ") _kora_count", query.getArgs());
    }
}
