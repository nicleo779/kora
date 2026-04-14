package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.SqlRequest;

public interface InsertRenderer {
    SqlRequest render(InsertModel insertModel, RenderContext context);
}
