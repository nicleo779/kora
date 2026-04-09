package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class PredicateBuilder {
    private final List<Condition> conditions = new ArrayList<>();

    public <T, V> PredicateBuilder eq(Column<T, V> column, V value) {
        return eq(true, column, value);
    }

    public <T, V> PredicateBuilder eq(boolean include, Column<T, V> column, V value) {
        return add(include, column.eq(value));
    }

    public <T, V> PredicateBuilder ne(Column<T, V> column, V value) {
        return ne(true, column, value);
    }

    public <T, V> PredicateBuilder ne(boolean include, Column<T, V> column, V value) {
        return add(include, column.ne(value));
    }

    public <T, V> PredicateBuilder gt(Column<T, V> column, V value) {
        return gt(true, column, value);
    }

    public <T, V> PredicateBuilder gt(boolean include, Column<T, V> column, V value) {
        return add(include, column.gt(value));
    }

    public <T, V> PredicateBuilder ge(Column<T, V> column, V value) {
        return ge(true, column, value);
    }

    public <T, V> PredicateBuilder ge(boolean include, Column<T, V> column, V value) {
        return add(include, column.ge(value));
    }

    public <T, V> PredicateBuilder lt(Column<T, V> column, V value) {
        return lt(true, column, value);
    }

    public <T, V> PredicateBuilder lt(boolean include, Column<T, V> column, V value) {
        return add(include, column.lt(value));
    }

    public <T, V> PredicateBuilder le(Column<T, V> column, V value) {
        return le(true, column, value);
    }

    public <T, V> PredicateBuilder le(boolean include, Column<T, V> column, V value) {
        return add(include, column.le(value));
    }

    public <T> PredicateBuilder like(Column<T, String> column, String value) {
        return like(true, column, value);
    }

    public <T> PredicateBuilder like(boolean include, Column<T, String> column, String value) {
        return add(include, column.like(value));
    }

    public <T, V> PredicateBuilder in(Column<T, V> column, Collection<? extends V> values) {
        return in(true, column, values);
    }

    public <T, V> PredicateBuilder in(boolean include, Column<T, V> column, Collection<? extends V> values) {
        return add(include, column.in(values));
    }

    public <T, V> PredicateBuilder notIn(Column<T, V> column, Collection<? extends V> values) {
        return notIn(true, column, values);
    }

    public <T, V> PredicateBuilder notIn(boolean include, Column<T, V> column, Collection<? extends V> values) {
        return add(include, column.notIn(values));
    }

    public <T, V> PredicateBuilder between(Column<T, V> column, V start, V end) {
        return between(true, column, start, end);
    }

    public <T, V> PredicateBuilder between(boolean include, Column<T, V> column, V start, V end) {
        return add(include, column.between(start, end));
    }

    public <T, V> PredicateBuilder isNull(Column<T, V> column) {
        return isNull(true, column);
    }

    public <T, V> PredicateBuilder isNull(boolean include, Column<T, V> column) {
        return add(include, column.isNull());
    }

    public <T, V> PredicateBuilder isNotNull(Column<T, V> column) {
        return isNotNull(true, column);
    }

    public <T, V> PredicateBuilder isNotNull(boolean include, Column<T, V> column) {
        return add(include, column.isNotNull());
    }

    private PredicateBuilder add(boolean include, Condition condition) {
        if (include) {
            conditions.add(condition);
        }
        return this;
    }

    Condition build() {
        if (conditions.isEmpty()) {
            return null;
        }
        return (sql, args) -> {
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sql.append(" AND ");
                }
                conditions.get(i).appendTo(sql, args);
            }
        };
    }
}
