package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.SqlSessionException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class QueryWrapper<T> {
    private final Class<T> entityType;
    private final List<String> selectExpressions = new ArrayList<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<Column<?, ?>> groupByColumns = new ArrayList<>();
    private final List<Order> orders = new ArrayList<>();
    private EntityTable<?> from;
    private Condition where;
    private Integer limit;
    private Integer offset;
    private boolean selectAll;

    QueryWrapper(Class<T> entityType) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    public Class<T> entityType() {
        return entityType;
    }

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
        PredicateBuilder builder = new PredicateBuilder();
        consumer.accept(builder);
        this.where = builder.build();
        return this;
    }

    public QueryWrapper<T> groupBy(Column<?, ?>... columns) {
        groupByColumns.clear();
        groupByColumns.addAll(List.of(columns));
        return this;
    }

    public QueryWrapper<T> orderBy(Consumer<OrderBuilder> consumer) {
        OrderBuilder builder = new OrderBuilder();
        consumer.accept(builder);
        orders.clear();
        orders.addAll(builder.build());
        return this;
    }

    public QueryWrapper<T> limit(int limit) {
        this.offset = null;
        this.limit = limit;
        return this;
    }

    public QueryWrapper<T> limit(int offset, int limit) {
        this.offset = offset;
        this.limit = limit;
        return this;
    }

    public SqlRequest toSqlRequest() {
        if (from == null) {
            throw new SqlSessionException("QueryWrapper requires from(table) before rendering SQL");
        }
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (selectAll || selectExpressions.isEmpty()) {
            sql.append(from.qualifier()).append(".*");
        } else {
            sql.append(String.join(", ", selectExpressions));
        }
        sql.append(" FROM ").append(from.tableReference());
        for (JoinSpec join : joins) {
            sql.append(' ').append(join.joinType()).append(' ').append(join.table().tableReference()).append(" ON ");
            join.on().appendTo(sql, args);
        }
        if (where != null) {
            sql.append(" WHERE ");
            where.appendTo(sql, args);
        }
        if (!groupByColumns.isEmpty()) {
            sql.append(" GROUP BY ");
            for (int i = 0; i < groupByColumns.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(groupByColumns.get(i).expression());
            }
        }
        if (!orders.isEmpty()) {
            sql.append(" ORDER BY ");
            for (int i = 0; i < orders.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                orders.get(i).appendTo(sql);
            }
        }
        if (limit != null) {
            sql.append(" LIMIT ?");
            args.add(limit);
            if (offset != null) {
                sql.append(" OFFSET ?");
                args.add(offset);
            }
        }
        return new SqlRequest(sql.toString(), args.toArray());
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
