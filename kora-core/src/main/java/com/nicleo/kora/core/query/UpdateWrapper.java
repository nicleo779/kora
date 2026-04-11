package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlSessionException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class UpdateWrapper {
    private final List<UpdateAssignment> assignments = new ArrayList<>();
    private final WhereWrapper whereWrapper = new WhereWrapper();

    public <T, V> UpdateWrapper set(Column<T, V> column, V value) {
        return set(true, column, value);
    }

    public <T, V> UpdateWrapper set(boolean include, Column<T, V> column, V value) {
        return set(include, column, Expressions.literal(value));
    }

    public <T> UpdateWrapper set(Column<T, ?> column, SqlExpression value) {
        return set(true, column, value);
    }

    public <T> UpdateWrapper set(boolean include, Column<T, ?> column, SqlExpression value) {
        if (include) {
            assignments.add(new UpdateAssignment(column, value));
        }
        return this;
    }

    public <T, N extends Number> UpdateWrapper setIncrBy(Column<T, N> column, N value) {
        return setIncrBy(true, column, value);
    }

    public <T, N extends Number> UpdateWrapper setIncrBy(boolean include, Column<T, N> column, N value) {
        return set(include, column, Expressions.add(column, value));
    }

    public <T, N extends Number> UpdateWrapper setDecrBy(Column<T, N> column, N value) {
        return setDecrBy(true, column, value);
    }

    public <T, N extends Number> UpdateWrapper setDecrBy(boolean include, Column<T, N> column, N value) {
        return set(include, column, Expressions.subtract(column, value));
    }

    public UpdateWrapper where(Consumer<PredicateBuilder> consumer) {
        whereWrapper.where(consumer);
        return this;
    }

    public UpdateWrapper limit(int limit) {
        whereWrapper.limit(limit);
        return this;
    }

    public UpdateWrapper limit(int offset, int limit) {
        whereWrapper.limit(offset, limit);
        return this;
    }

    public UpdateDefinition toDefinition() {
        if (assignments.isEmpty()) {
            throw new SqlSessionException("UpdateWrapper requires at least one set(...) clause");
        }
        return new UpdateDefinition(List.copyOf(assignments), whereWrapper.toDefinition());
    }
}
