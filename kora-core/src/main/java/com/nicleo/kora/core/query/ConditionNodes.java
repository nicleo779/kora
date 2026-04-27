package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.RenderContext;

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
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            left.appendTo(context);
            context.sql().append(' ').append(operator).append(" ?");
            context.args().add(value);
        }
    }

    record ExpressionCondition(SqlExpression left, String operator, SqlExpression right) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            left.appendTo(context);
            context.sql().append(' ').append(operator).append(' ');
            right.appendTo(context);
        }
    }

    record NullCondition(SqlExpression expression, boolean negated) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            expression.appendTo(context);
            context.sql().append(negated ? " IS NOT NULL" : " IS NULL");
        }
    }

    record InCondition(SqlExpression expression, String operator, Collection<?> items,
                       boolean whenEmptyAlwaysTrue) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            if (items.isEmpty()) {
                context.sql().append(whenEmptyAlwaysTrue ? "1 = 1" : "1 = 0");
                return;
            }
            expression.appendTo(context);
            context.sql().append(' ').append(operator).append(" (")
                    .append(items.stream().map(e -> "?").collect(Collectors.joining(", ")));
            context.sql().append(')');
            context.args().addAll(items);
        }
    }

    record BetweenCondition(SqlExpression expression, Object start, Object end) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            expression.appendTo(context);
            context.sql().append(" BETWEEN ? AND ?");
            context.args().add(start);
            context.args().add(end);
        }
    }

    record GroupCondition(Condition condition) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            context.sql().append('(');
            condition.appendTo(context);
            context.sql().append(')');
        }
    }

    record NotCondition(Condition condition) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            context.sql().append("NOT (");
            condition.appendTo(context);
            context.sql().append(')');
        }
    }

    record CompositeCondition(String operator, List<Condition> conditions) implements ConditionNode {
        @Override
        public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
            appendTo(RenderContext.bridge(dbType, sql, args));
        }

        @Override
        public void appendTo(RenderContext context) {
            context.sql().append('(');
            for (int i = 0; i < conditions.size(); i++) {
                if (i > 0) {
                    context.sql().append(' ').append(operator).append(' ');
                }
                conditions.get(i).appendTo(context);
            }
            context.sql().append(')');
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
