package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.List;

public final class Column<T, V> {
    private final EntityTable<T> table;
    private final String columnName;
    private final Class<V> javaType;

    Column(EntityTable<T> table, String columnName, Class<V> javaType) {
        this.table = table;
        this.columnName = columnName;
        this.javaType = javaType;
    }

    public EntityTable<T> table() {
        return table;
    }

    public String columnName() {
        return columnName;
    }

    public Class<V> javaType() {
        return javaType;
    }

    public String expression() {
        return table.qualifier() + "." + columnName;
    }

    public Order asc() {
        return new Order(expression(), true);
    }

    public Order desc() {
        return new Order(expression(), false);
    }

    public Condition eq(Object value) {
        return valueCondition("=", value);
    }

    public Condition ne(Object value) {
        return valueCondition("<>", value);
    }

    public Condition gt(Object value) {
        return valueCondition(">", value);
    }

    public Condition ge(Object value) {
        return valueCondition(">=", value);
    }

    public Condition lt(Object value) {
        return valueCondition("<", value);
    }

    public Condition le(Object value) {
        return valueCondition("<=", value);
    }

    public Condition like(String value) {
        return valueCondition("LIKE", value);
    }

    public Condition isNull() {
        return (sql, args) -> sql.append(expression()).append(" IS NULL");
    }

    public Condition isNotNull() {
        return (sql, args) -> sql.append(expression()).append(" IS NOT NULL");
    }

    public Condition in(Iterable<?> values) {
        return iterableCondition("IN", values, false);
    }

    public Condition notIn(Iterable<?> values) {
        return iterableCondition("NOT IN", values, true);
    }

    public Condition between(Object start, Object end) {
        return (sql, args) -> {
            sql.append(expression()).append(" BETWEEN ? AND ?");
            args.add(start);
            args.add(end);
        };
    }

    public Condition eq(Column<?, ?> other) {
        return (sql, args) -> sql.append(expression()).append(" = ").append(other.expression());
    }

    private Condition valueCondition(String operator, Object value) {
        return (sql, args) -> {
            sql.append(expression()).append(' ').append(operator).append(" ?");
            args.add(value);
        };
    }

    private Condition iterableCondition(String operator, Iterable<?> values, boolean whenEmptyAlwaysTrue) {
        List<Object> items = new ArrayList<>();
        for (Object value : values) {
            items.add(value);
        }
        return (sql, args) -> {
            if (items.isEmpty()) {
                sql.append(whenEmptyAlwaysTrue ? "1 = 1" : "1 = 0");
                return;
            }
            sql.append(expression()).append(' ').append(operator).append(" (");
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append('?');
                args.add(items.get(i));
            }
            sql.append(')');
        };
    }
}
