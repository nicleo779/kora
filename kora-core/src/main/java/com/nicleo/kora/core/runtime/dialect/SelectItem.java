package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.SqlExpression;

public record SelectItem(
        SqlExpression expression,
        String alias
) {
    public boolean aliased() {
        return alias != null && !alias.isBlank();
    }
}
