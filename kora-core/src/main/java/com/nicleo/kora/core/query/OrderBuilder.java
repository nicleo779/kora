package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.List;

public final class OrderBuilder {
    private final List<Order> orders = new ArrayList<>();

    public OrderBuilder asc(Column<?, ?> column) {
        orders.add(column.asc());
        return this;
    }

    public OrderBuilder desc(Column<?, ?> column) {
        orders.add(column.desc());
        return this;
    }

    List<Order> build() {
        return List.copyOf(orders);
    }
}
