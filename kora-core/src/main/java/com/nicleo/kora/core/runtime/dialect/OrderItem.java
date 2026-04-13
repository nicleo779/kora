package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.SqlExpression;

public record OrderItem(
        SqlExpression expression,
        boolean ascending
) {
}
