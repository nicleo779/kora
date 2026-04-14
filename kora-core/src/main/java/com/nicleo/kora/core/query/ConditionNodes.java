package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

final class ConditionNodes {
    private ConditionNodes() {
    }

    record ValueCondition(SqlExpression left, String operator, Object value) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            left.appendTo(sql, args, dbType);
            sql.append(' ').append(operator).append(" ?");
            args.add(value);
        }
    }

    record ExpressionCondition(SqlExpression left, String operator, SqlExpression right) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            left.appendTo(sql, args, dbType);
            sql.append(' ').append(operator).append(' ');
            right.appendTo(sql, args, dbType);
        }
    }

    record NullCondition(SqlExpression expression, boolean negated) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            expression.appendTo(sql, args, dbType);
            sql.append(negated ? " IS NOT NULL" : " IS NULL");
        }
    }

    record InCondition(SqlExpression expression, String operator, Collection<?> items,
                       boolean whenEmptyAlwaysTrue) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            if (items.isEmpty()) {
                sql.append(whenEmptyAlwaysTrue ? "1 = 1" : "1 = 0");
                return;
            }
            expression.appendTo(sql, args, dbType);
            sql.append(' ').append(operator).append(" (").append(items.stream().map(e -> "?").collect(Collectors.joining(", ")));
            sql.append(')');
            args.addAll(items);
        }
    }

    record BetweenCondition(SqlExpression expression, Object start, Object end) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            expression.appendTo(sql, args, dbType);
            sql.append(" BETWEEN ? AND ?");
            args.add(start);
            args.add(end);
        }
    }

    record GroupCondition(Condition condition) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            sql.append('(');
            condition.appendTo(sql, args, dbType);
            sql.append(')');
        }
    }

    record NotCondition(Condition condition) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            sql.append("NOT (");
            condition.appendTo(sql, args, dbType);
            sql.append(')');
        }
    }

    record CompositeCondition(String operator, List<Condition> conditions) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            sql.append('(');
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    sql.append(' ').append(operator).append(' ');
                }
                conditions.get(i).appendTo(sql, args, dbType);
            }
            sql.append(')');
        }
    }

    private static List<Object> copy(Iterable<?> values) {
        List<Object> items = new ArrayList<>();
        for (Object value : values) {
            items.add(value);
        }
        return List.copyOf(items);
    }
}
