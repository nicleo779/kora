package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class WhereWrapper {
    private final List<Order> orders = new ArrayList<>();
    private Condition where;
    private Integer limit;
    private Integer offset;

    public WhereWrapper where(Consumer<PredicateBuilder> consumer) {
        PredicateBuilder builder = new PredicateBuilder();
        consumer.accept(builder);
        this.where = builder.build();
        return this;
    }

    public WhereWrapper condition(Condition condition) {
        this.where = condition;
        return this;
    }

    public WhereWrapper where(Condition... conditions) {
        this.where = Conditions.and(conditions);
        return this;
    }

    public WhereWrapper orderBy(Consumer<OrderBuilder> consumer) {
        OrderBuilder builder = new OrderBuilder();
        consumer.accept(builder);
        orders.clear();
        orders.addAll(builder.build());
        return this;
    }

    public WhereWrapper limit(int limit) {
        this.offset = null;
        this.limit = limit;
        return this;
    }

    public WhereWrapper limit(int offset, int limit) {
        this.offset = offset;
        this.limit = limit;
        return this;
    }

    public WhereDefinition toDefinition() {
        return new WhereDefinition(where, List.copyOf(orders), limit, offset);
    }
}
