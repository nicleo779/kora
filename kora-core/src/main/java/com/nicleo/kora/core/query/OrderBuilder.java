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

    List<Order> build() {
        return List.copyOf(orders);
    }
}
