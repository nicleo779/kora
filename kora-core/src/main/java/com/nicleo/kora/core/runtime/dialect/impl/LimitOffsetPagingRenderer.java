package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.dialect.PageClause;
import com.nicleo.kora.core.runtime.dialect.PagingRenderer;
import com.nicleo.kora.core.runtime.dialect.RenderContext;

public final class LimitOffsetPagingRenderer implements PagingRenderer {
    @Override
    public void render(PageClause pageClause, RenderContext context) {
        if (pageClause.limit() == null) {
            return;
        }
        context.sql().append(" LIMIT ?");
        context.args().add(pageClause.limit());
        if (pageClause.offset() != null) {
            context.sql().append(" OFFSET ?");
            context.args().add(pageClause.offset());
        }
    }
}
