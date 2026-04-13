package com.nicleo.kora.core.runtime.dialect;

public interface PagingRenderer {
    void render(PageClause pageClause, RenderContext context);
}
