package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.QueryDefinition;

import java.util.List;

public record QueryModel(
        QueryDefinition definition,
        List<SelectItem> selectItems,
        List<JoinItem> joinItems,
        List<GroupByItem> groupByItems,
        List<OrderItem> orderItems,
        WhereClause whereClause,
        HavingClause havingClause
) {
}
