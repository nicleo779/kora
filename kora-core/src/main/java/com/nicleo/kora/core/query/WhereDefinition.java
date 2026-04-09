package com.nicleo.kora.core.query;

import java.util.List;

public record WhereDefinition(
        Condition condition,
        List<Order> orders,
        Integer limit,
        Integer offset
) {
}
