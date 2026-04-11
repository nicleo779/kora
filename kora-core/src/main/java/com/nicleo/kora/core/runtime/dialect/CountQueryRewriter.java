package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.SqlRequest;

public interface CountQueryRewriter {
    SqlRequest rewrite(QueryModel queryModel, RenderContext context);
}
