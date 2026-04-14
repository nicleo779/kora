package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.SqlRequest;

public interface DeleteRenderer {
    SqlRequest render(DeleteModel deleteModel, RenderContext context);
}
