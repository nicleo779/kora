package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public final class PredicateBuilder {
    private final List<Segment> segments = new ArrayList<>();

    public PredicateBuilder condition(Condition condition) {
        return condition(true, condition);
    }

    public PredicateBuilder condition(boolean include, Condition condition) {
        return add("AND", include, condition);
    }

    public PredicateBuilder and(Consumer<PredicateBuilder> consumer) {
        return and(true, consumer);
    }

    public PredicateBuilder and(boolean include, Consumer<PredicateBuilder> consumer) {
        return add("AND", include, nested(consumer));
    }

    public PredicateBuilder or(Consumer<PredicateBuilder> consumer) {
        return or(true, consumer);
    }

    public PredicateBuilder or(boolean include, Consumer<PredicateBuilder> consumer) {
        return add("OR", include, nested(consumer));
    }

    public PredicateBuilder and(Condition condition) {
        return add("AND", true, condition);
    }

    public PredicateBuilder and(boolean include, Condition condition) {
        return add("AND", include, condition);
    }

    public PredicateBuilder or(Condition condition) {
        return add("OR", true, condition);
    }

    public PredicateBuilder or(boolean include, Condition condition) {
        return add("OR", include, condition);
    }

    public PredicateBuilder not(Consumer<PredicateBuilder> consumer) {
        return not(true, consumer);
    }

    public PredicateBuilder not(boolean include, Consumer<PredicateBuilder> consumer) {
        return add("AND", include, Conditions.not(nestedRaw(consumer)));
    }

    public PredicateBuilder not(Condition condition) {
        return not(true, condition);
    }

    public PredicateBuilder not(boolean include, Condition condition) {
        return add("AND", include, Conditions.not(condition));
    }

    public PredicateBuilder eq(SqlExpression expression, Object value) {
        return eq(true, expression, value);
    }

    public PredicateBuilder eq(boolean include, SqlExpression expression, Object value) {
        return add("AND", include, Conditions.eq(expression, value));
    }

    public <T, V> PredicateBuilder eq(Column<T, V> column, V value) {
        return eq(true, column, value);
    }

    public <T, V> PredicateBuilder eq(boolean include, Column<T, V> column, V value) {
        return eq(include, (SqlExpression) column, value);
    }

    public PredicateBuilder ne(SqlExpression expression, Object value) {
        return ne(true, expression, value);
    }

    public PredicateBuilder ne(boolean include, SqlExpression expression, Object value) {
        return add("AND", include, Conditions.ne(expression, value));
    }

    public <T, V> PredicateBuilder ne(Column<T, V> column, V value) {
        return ne(true, column, value);
    }

    public <T, V> PredicateBuilder ne(boolean include, Column<T, V> column, V value) {
        return ne(include, (SqlExpression) column, value);
    }

    public PredicateBuilder gt(SqlExpression expression, Object value) {
        return gt(true, expression, value);
    }

    public PredicateBuilder gt(boolean include, SqlExpression expression, Object value) {
        return add("AND", include, Conditions.gt(expression, value));
    }

    public <T, V> PredicateBuilder gt(Column<T, V> column, V value) {
        return gt(true, column, value);
    }

    public <T, V> PredicateBuilder gt(boolean include, Column<T, V> column, V value) {
        return gt(include, (SqlExpression) column, value);
    }

    public PredicateBuilder ge(SqlExpression expression, Object value) {
        return ge(true, expression, value);
    }

    public PredicateBuilder ge(boolean include, SqlExpression expression, Object value) {
        return add("AND", include, Conditions.ge(expression, value));
    }

    public <T, V> PredicateBuilder ge(Column<T, V> column, V value) {
        return ge(true, column, value);
    }

    public <T, V> PredicateBuilder ge(boolean include, Column<T, V> column, V value) {
        return ge(include, (SqlExpression) column, value);
    }

    public PredicateBuilder lt(SqlExpression expression, Object value) {
        return lt(true, expression, value);
    }

    public PredicateBuilder lt(boolean include, SqlExpression expression, Object value) {
        return add("AND", include, Conditions.lt(expression, value));
    }

    public <T, V> PredicateBuilder lt(Column<T, V> column, V value) {
        return lt(true, column, value);
    }

    public <T, V> PredicateBuilder lt(boolean include, Column<T, V> column, V value) {
        return lt(include, (SqlExpression) column, value);
    }

    public PredicateBuilder le(SqlExpression expression, Object value) {
        return le(true, expression, value);
    }

    public PredicateBuilder le(boolean include, SqlExpression expression, Object value) {
        return add("AND", include, Conditions.le(expression, value));
    }

    public <T, V> PredicateBuilder le(Column<T, V> column, V value) {
        return le(true, column, value);
    }

    public <T, V> PredicateBuilder le(boolean include, Column<T, V> column, V value) {
        return le(include, (SqlExpression) column, value);
    }

    public PredicateBuilder like(SqlExpression expression, String value) {
        return like(true, expression, value);
    }

    public PredicateBuilder like(boolean include, SqlExpression expression, String value) {
        return add("AND", include, Conditions.like(expression, value));
    }

    public <T> PredicateBuilder like(Column<T, String> column, String value) {
        return like(true, column, value);
    }

    public <T> PredicateBuilder like(boolean include, Column<T, String> column, String value) {
        return like(include, (SqlExpression) column, value);
    }

    public PredicateBuilder in(SqlExpression expression, Collection<?> values) {
        return in(true, expression, values);
    }

    public PredicateBuilder in(boolean include, SqlExpression expression, Collection<?> values) {
        return add("AND", include, Conditions.in(expression, values));
    }

    public <T, V> PredicateBuilder in(Column<T, V> column, Collection<? extends V> values) {
        return in(true, column, values);
    }

    public <T, V> PredicateBuilder in(boolean include, Column<T, V> column, Collection<? extends V> values) {
        return in(include, (SqlExpression) column, values);
    }

    public PredicateBuilder notIn(SqlExpression expression, Collection<?> values) {
        return notIn(true, expression, values);
    }

    public PredicateBuilder notIn(boolean include, SqlExpression expression, Collection<?> values) {
        return add("AND", include, Conditions.notIn(expression, values));
    }

    public <T, V> PredicateBuilder notIn(Column<T, V> column, Collection<? extends V> values) {
        return notIn(true, column, values);
    }

    public <T, V> PredicateBuilder notIn(boolean include, Column<T, V> column, Collection<? extends V> values) {
        return notIn(include, (SqlExpression) column, values);
    }

    public PredicateBuilder between(SqlExpression expression, Object start, Object end) {
        return between(true, expression, start, end);
    }

    public PredicateBuilder between(boolean include, SqlExpression expression, Object start, Object end) {
        return add("AND", include, Conditions.between(expression, start, end));
    }

    public <T, V> PredicateBuilder between(Column<T, V> column, V start, V end) {
        return between(true, column, start, end);
    }

    public <T, V> PredicateBuilder between(boolean include, Column<T, V> column, V start, V end) {
        return between(include, (SqlExpression) column, start, end);
    }

    public PredicateBuilder isNull(SqlExpression expression) {
        return isNull(true, expression);
    }

    public PredicateBuilder isNull(boolean include, SqlExpression expression) {
        return add("AND", include, Conditions.isNull(expression));
    }

    public <T, V> PredicateBuilder isNull(Column<T, V> column) {
        return isNull(true, column);
    }

    public <T, V> PredicateBuilder isNull(boolean include, Column<T, V> column) {
        return isNull(include, (SqlExpression) column);
    }

    public PredicateBuilder isNotNull(SqlExpression expression) {
        return isNotNull(true, expression);
    }

    public PredicateBuilder isNotNull(boolean include, SqlExpression expression) {
        return add("AND", include, Conditions.isNotNull(expression));
    }

    public <T, V> PredicateBuilder isNotNull(Column<T, V> column) {
        return isNotNull(true, column);
    }

    public <T, V> PredicateBuilder isNotNull(boolean include, Column<T, V> column) {
        return isNotNull(include, (SqlExpression) column);
    }

    private PredicateBuilder add(String operator, boolean include, Condition condition) {
        if (include && condition != null) {
            segments.add(new Segment(operator, condition));
        }
        return this;
    }

    Condition build() {
        if (segments.isEmpty()) {
            return null;
        }
        return (sql, args, dbType) -> {
            for (int i = 0; i < segments.size(); i++) {
                if (i > 0) {
                    sql.append(' ').append(segments.get(i).operator()).append(' ');
                }
                segments.get(i).condition().appendTo(sql, args, dbType);
            }
        };
    }

    private Condition nested(Consumer<PredicateBuilder> consumer) {
        return Conditions.group(nestedRaw(consumer));
    }

    private Condition nestedRaw(Consumer<PredicateBuilder> consumer) {
        PredicateBuilder builder = new PredicateBuilder();
        consumer.accept(builder);
        return builder.build();
    }

    private record Segment(String operator, Condition condition) {
    }
}
