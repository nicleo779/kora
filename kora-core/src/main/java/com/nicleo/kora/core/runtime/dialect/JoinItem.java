package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.Condition;
import com.nicleo.kora.core.query.EntityTable;

public record JoinItem(
        String joinType,
        EntityTable<?> table,
        Condition on
) {
}
