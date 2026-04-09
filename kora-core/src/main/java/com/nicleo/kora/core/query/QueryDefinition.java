package com.nicleo.kora.core.query;

import java.util.List;

public record QueryDefinition(
        List<String> selectExpressions,
        boolean selectAll,
        EntityTable<?> from,
        List<QueryJoin> joins,
        List<Column<?, ?>> groupByColumns,
        WhereDefinition where
) {
}
