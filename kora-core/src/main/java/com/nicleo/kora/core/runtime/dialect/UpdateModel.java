package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.UpdateDefinition;

public record UpdateModel(
        EntityTable<?> table,
        UpdateDefinition definition
) {
}
