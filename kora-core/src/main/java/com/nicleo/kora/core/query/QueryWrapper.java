package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlSessionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class QueryWrapper<T> {
    private final List<SqlExpression> selectExpressions = new ArrayList<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<SqlExpression> groupByExpressions = new ArrayList<>();
    private final WhereWrapper<T> whereWrapper = new WhereWrapper<>();
    private Condition having;
    private EntityTable<?> from;
    private boolean selectAll;

    public QueryWrapper<T> select(SqlExpression... expressions) {
        selectExpressions.addAll(List.of(expressions));
        return this;
    }

    public QueryWrapper<T> selectAll() {
        this.selectAll = true;
        return this;
    }

    public QueryWrapper<T> from(EntityTable<?> table) {
        this.from = Objects.requireNonNull(table, "table");
        return this;
    }

    public JoinStep<T> leftJoin(EntityTable<?> table) {
        return new JoinStep<>(this, "LEFT JOIN", table);
    }

    public JoinStep<T> innerJoin(EntityTable<?> table) {
        return new JoinStep<>(this, "INNER JOIN", table);
    }

    public JoinStep<T> rightJoin(EntityTable<?> table) {
        return new JoinStep<>(this, "RIGHT JOIN", table);
    }

    public QueryWrapper<T> where(Consumer<PredicateBuilder> consumer) {
        whereWrapper.where(consumer);
        return this;
    }

    public QueryWrapper<T> groupBy(SqlExpression... expressions) {
        groupByExpressions.clear();
        groupByExpressions.addAll(List.of(expressions));
        return this;
    }

    public QueryWrapper<T> having(Consumer<PredicateBuilder> consumer) {
        PredicateBuilder builder = new PredicateBuilder();
        consumer.accept(builder);
        this.having = builder.build();
        return this;
    }

    public QueryWrapper<T> orderBy(Consumer<OrderBuilder> consumer) {
        whereWrapper.orderBy(consumer);
        return this;
    }

    public QueryWrapper<T> limit(int limit) {
        whereWrapper.limit(limit);
        return this;
    }

    public QueryWrapper<T> limit(int offset, int limit) {
        whereWrapper.limit(offset, limit);
        return this;
    }

    public QueryDefinition toDefinition() {
        if (from == null) {
            throw new SqlSessionException("QueryWrapper requires from(table) before rendering SQL");
        }
        return new QueryDefinition(
                List.copyOf(selectExpressions),
                selectAll,
                from,
                joins.stream().map(join -> new QueryJoin(join.joinType(), join.table(), join.on())).toList(),
                List.copyOf(groupByExpressions),
                having,
                whereWrapper.toDefinition()
        );
    }

    private void addJoin(String joinType, EntityTable<?> table, Condition on) {
        joins.add(new JoinSpec(joinType, table, on));
    }

    public static final class JoinStep<T> {
        private final QueryWrapper<T> owner;
        private final String joinType;
        private final EntityTable<?> table;

        private JoinStep(QueryWrapper<T> owner, String joinType, EntityTable<?> table) {
            this.owner = owner;
            this.joinType = joinType;
            this.table = table;
        }

        public QueryWrapper<T> on(Condition condition) {
            owner.addJoin(joinType, table, condition);
            return owner;
        }

        public QueryWrapper<T> on(Consumer<PredicateBuilder> consumer) {
            PredicateBuilder builder = new PredicateBuilder();
            consumer.accept(builder);
            return on(builder.build());
        }
    }

    private record JoinSpec(String joinType, EntityTable<?> table, Condition on) {
    }
}
