package com.nicleo.kora.core.query;

import java.util.List;

public record QueryDefinition(
        List<SqlExpression> selectExpressions,
        boolean selectAll,
        EntityTable<?> from,
        List<QueryJoin> joins,
        List<SqlExpression> groupByExpressions,
        Condition having,
        WhereDefinition where
) {
}
