package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.List;

public final class Conditions {
    private Conditions() {
    }

    public static Condition eq(SqlExpression left, Object value) {
        return valueCondition(left, "=", value);
    }

    public static Condition ne(SqlExpression left, Object value) {
        return valueCondition(left, "<>", value);
    }

    public static Condition gt(SqlExpression left, Object value) {
        return valueCondition(left, ">", value);
    }

    public static Condition ge(SqlExpression left, Object value) {
        return valueCondition(left, ">=", value);
    }

    public static Condition lt(SqlExpression left, Object value) {
        return valueCondition(left, "<", value);
    }

    public static Condition le(SqlExpression left, Object value) {
        return valueCondition(left, "<=", value);
    }

    public static Condition like(SqlExpression left, String value) {
        return valueCondition(left, "LIKE", value);
    }

    public static Condition isNull(SqlExpression expression) {
        return (sql, args, dbType) -> {
            expression.appendTo(sql, args, dbType);
            sql.append(" IS NULL");
        };
    }

    public static Condition isNotNull(SqlExpression expression) {
        return (sql, args, dbType) -> {
            expression.appendTo(sql, args, dbType);
            sql.append(" IS NOT NULL");
        };
    }

    public static Condition in(SqlExpression expression, Iterable<?> values) {
        return iterableCondition(expression, "IN", values, false);
    }

    public static Condition notIn(SqlExpression expression, Iterable<?> values) {
        return iterableCondition(expression, "NOT IN", values, true);
    }

    public static Condition between(SqlExpression expression, Object start, Object end) {
        return (sql, args, dbType) -> {
            expression.appendTo(sql, args, dbType);
            sql.append(" BETWEEN ? AND ?");
            args.add(start);
            args.add(end);
        };
    }

    public static Condition eq(SqlExpression left, SqlExpression right) {
        return (sql, args, dbType) -> {
            left.appendTo(sql, args, dbType);
            sql.append(" = ");
            right.appendTo(sql, args, dbType);
        };
    }

    public static Condition group(Condition condition) {
        if (condition == null) {
            return null;
        }
        return (sql, args, dbType) -> {
            sql.append('(');
            condition.appendTo(sql, args, dbType);
            sql.append(')');
        };
    }

    public static Condition and(Condition... conditions) {
        return combine("AND", conditions);
    }

    public static Condition or(Condition... conditions) {
        return combine("OR", conditions);
    }

    public static Condition not(Condition condition) {
        if (condition == null) {
            return null;
        }
        return (sql, args, dbType) -> {
            sql.append("NOT (");
            condition.appendTo(sql, args, dbType);
            sql.append(')');
        };
    }

    private static Condition valueCondition(SqlExpression left, String operator, Object value) {
        return (sql, args, dbType) -> {
            left.appendTo(sql, args, dbType);
            sql.append(' ').append(operator).append(" ?");
            args.add(value);
        };
    }

    private static Condition iterableCondition(SqlExpression expression, String operator, Iterable<?> values, boolean whenEmptyAlwaysTrue) {
        List<Object> items = new ArrayList<>();
        for (Object value : values) {
            items.add(value);
        }
        return (sql, args, dbType) -> {
            if (items.isEmpty()) {
                sql.append(whenEmptyAlwaysTrue ? "1 = 1" : "1 = 0");
                return;
            }
            expression.appendTo(sql, args, dbType);
            sql.append(' ').append(operator).append(" (");
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

    private static Condition combine(String operator, Condition... conditions) {
        List<Condition> filtered = new ArrayList<>();
        for (Condition condition : conditions) {
            if (condition != null) {
                filtered.add(condition);
            }
        }
        if (filtered.isEmpty()) {
            return null;
        }
        if (filtered.size() == 1) {
            return filtered.get(0);
        }
        return (sql, args, dbType) -> {
            sql.append('(');
            for (int i = 0; i < filtered.size(); i++) {
                if (i > 0) {
                    sql.append(' ').append(operator).append(' ');
                }
                filtered.get(i).appendTo(sql, args, dbType);
            }
            sql.append(')');
        };
    }
}
