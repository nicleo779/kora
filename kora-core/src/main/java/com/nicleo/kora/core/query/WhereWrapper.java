package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class WhereWrapper<T> {
    private final List<Order> orders = new ArrayList<>();
    private Condition where;
    private Integer limit;
    private Integer offset;

    public WhereWrapper<T> where(Consumer<PredicateBuilder> consumer) {
        PredicateBuilder builder = new PredicateBuilder();
        consumer.accept(builder);
        this.where = builder.build();
        return this;
    }

    public WhereWrapper<T> orderBy(Consumer<OrderBuilder> consumer) {
        OrderBuilder builder = new OrderBuilder();
        consumer.accept(builder);
        orders.clear();
        orders.addAll(builder.build());
        return this;
    }

    public WhereWrapper<T> limit(int limit) {
        this.offset = null;
        this.limit = limit;
        return this;
    }

    public WhereWrapper<T> limit(int offset, int limit) {
        this.offset = offset;
        this.limit = limit;
        return this;
    }

    public WhereDefinition toDefinition() {
        return new WhereDefinition(where, List.copyOf(orders), limit, offset);
    }
}
