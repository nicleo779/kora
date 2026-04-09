package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlSessionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class QueryWrapper<T> {
    private final List<String> selectExpressions = new ArrayList<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<Column<?, ?>> groupByColumns = new ArrayList<>();
    private final WhereWrapper<T> whereWrapper = new WhereWrapper<>();
    private EntityTable<?> from;
    private boolean selectAll;

    public QueryWrapper<T> select(Column<?, ?>... columns) {
        for (Column<?, ?> column : columns) {
            selectExpressions.add(column.expression());
        }
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

    public QueryWrapper<T> where(Consumer<PredicateBuilder> consumer) {
        whereWrapper.where(consumer);
        return this;
    }

    public QueryWrapper<T> groupBy(Column<?, ?>... columns) {
        groupByColumns.clear();
        groupByColumns.addAll(List.of(columns));
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
                List.copyOf(groupByColumns),
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
    }

    private record JoinSpec(String joinType, EntityTable<?> table, Condition on) {
    }
}
