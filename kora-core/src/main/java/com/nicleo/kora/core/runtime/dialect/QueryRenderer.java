package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.SqlRequest;

public interface QueryRenderer {
    SqlRequest render(QueryModel queryModel, RenderContext context);
}
