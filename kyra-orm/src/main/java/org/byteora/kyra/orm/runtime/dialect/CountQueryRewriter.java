package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.SqlRequest;

public interface CountQueryRewriter {
    SqlRequest rewrite(QueryModel queryModel, RenderContext context);

    SqlRequest rewriteRaw(String sql, Object[] args, RenderContext context);
}
