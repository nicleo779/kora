package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.List;

public final class PredicateBuilder {
    private final List<Condition> conditions = new ArrayList<>();

    public <T, V> PredicateBuilder eq(Column<T, V> column, V value) {
        conditions.add(column.eq(value));
        return this;
    }

    public <T, V> PredicateBuilder eqIfPresent(Column<T, V> column, V value) {
        if (value != null) {
            conditions.add(column.eq(value));
        }
        return this;
    }

    public <T, V> PredicateBuilder ge(Column<T, V> column, V value) {
        conditions.add(column.ge(value));
        return this;
    }

    public <T, V> PredicateBuilder geIfPresent(Column<T, V> column, V value) {
        if (value != null) {
            conditions.add(column.ge(value));
        }
        return this;
    }

    public <T, V> PredicateBuilder le(Column<T, V> column, V value) {
        conditions.add(column.le(value));
        return this;
    }

    public <T, V> PredicateBuilder leIfPresent(Column<T, V> column, V value) {
        if (value != null) {
            conditions.add(column.le(value));
        }
        return this;
    }

    public <T> PredicateBuilder likeIfPresent(Column<T, String> column, String value) {
        if (value != null && !value.isBlank()) {
            conditions.add(column.like(value));
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
