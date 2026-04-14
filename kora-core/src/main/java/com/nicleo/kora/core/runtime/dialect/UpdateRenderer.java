package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.SqlRequest;

public interface UpdateRenderer {
    SqlRequest render(UpdateModel updateModel, RenderContext context);
}
