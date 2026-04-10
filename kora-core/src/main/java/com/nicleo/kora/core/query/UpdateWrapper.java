package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlSessionException;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class UpdateWrapper<T> {
    private final List<UpdateAssignment> assignments = new ArrayList<>();
    private final WhereWrapper<T> whereWrapper = new WhereWrapper<>();

    public <V> UpdateWrapper<T> set(Column<T, V> column, V value) {
        return set(true, column, value);
    }

    public <V> UpdateWrapper<T> set(boolean include, Column<T, V> column, V value) {
        return set(include, column, Expressions.literal(value));
    }

    public UpdateWrapper<T> set(Column<T, ?> column, SqlExpression value) {
        return set(true, column, value);
    }

    public UpdateWrapper<T> set(boolean include, Column<T, ?> column, SqlExpression value) {
        if (include) {
            assignments.add(new UpdateAssignment(column, value));
        }
        return this;
    }

    public UpdateWrapper<T> where(Consumer<PredicateBuilder> consumer) {
        whereWrapper.where(consumer);
        return this;
    }

    public UpdateWrapper<T> limit(int limit) {
        whereWrapper.limit(limit);
        return this;
    }

    public UpdateWrapper<T> limit(int offset, int limit) {
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
