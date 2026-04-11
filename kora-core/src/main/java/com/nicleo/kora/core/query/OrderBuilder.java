package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.List;

public final class OrderBuilder {
    private final List<Order> orders = new ArrayList<>();

    public OrderBuilder asc(SqlExpression expression) {
        orders.add(expression.asc());
        return this;
    }

    public OrderBuilder desc(SqlExpression expression) {
        orders.add(expression.desc());
        return this;
    }

    public OrderBuilder asc(NamedSqlExpression expression) {
        return asc(expression.aliasRef());
    }

    public OrderBuilder desc(NamedSqlExpression expression) {
        return desc(expression.aliasRef());
    }

    public OrderBuilder ascAlias(String alias) {
        return asc(Expressions.aliasRef(alias));
    }

    public OrderBuilder descAlias(String alias) {
        return desc(Expressions.aliasRef(alias));
    }

    List<Order> build() {
        return List.copyOf(orders);
    }
}
