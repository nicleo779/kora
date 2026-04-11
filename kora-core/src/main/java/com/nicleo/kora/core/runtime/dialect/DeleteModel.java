package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.WhereDefinition;

public record DeleteModel(
        EntityTable<?> table,
        WhereDefinition where
) {
}
