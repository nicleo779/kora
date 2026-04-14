package com.nicleo.kora.core.query;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class Conditions {
    private Conditions() {
    }

    public static Condition eq(SqlExpression left, Object value) {
        return new ConditionNodes.ValueCondition(left, "=", value);
    }

    public static Condition eqAlias(String alias, Object value) {
        return eq(Expressions.aliasRef(alias), value);
    }

    public static Condition ne(SqlExpression left, Object value) {
        return new ConditionNodes.ValueCondition(left, "<>", value);
    }

    public static Condition neAlias(String alias, Object value) {
        return ne(Expressions.aliasRef(alias), value);
    }

    public static Condition gt(SqlExpression left, Object value) {
        return new ConditionNodes.ValueCondition(left, ">", value);
    }

    public static Condition gtAlias(String alias, Object value) {
        return gt(Expressions.aliasRef(alias), value);
    }

    public static Condition ge(SqlExpression left, Object value) {
        return new ConditionNodes.ValueCondition(left, ">=", value);
    }

    public static Condition geAlias(String alias, Object value) {
        return ge(Expressions.aliasRef(alias), value);
    }

    public static Condition lt(SqlExpression left, Object value) {
        return new ConditionNodes.ValueCondition(left, "<", value);
    }

    public static Condition ltAlias(String alias, Object value) {
        return lt(Expressions.aliasRef(alias), value);
    }

    public static Condition le(SqlExpression left, Object value) {
        return new ConditionNodes.ValueCondition(left, "<=", value);
    }

    public static Condition leAlias(String alias, Object value) {
        return le(Expressions.aliasRef(alias), value);
    }

    public static Condition like(SqlExpression left, String value) {
        return new ConditionNodes.ValueCondition(left, "LIKE", value);
    }

    public static Condition likeAlias(String alias, String value) {
        return like(Expressions.aliasRef(alias), value);
    }

    public static Condition isNull(SqlExpression expression) {
        return new ConditionNodes.NullCondition(expression, false);
    }

    public static Condition isNotNull(SqlExpression expression) {
        return new ConditionNodes.NullCondition(expression, true);
    }

    public static Condition in(SqlExpression expression, Collection<?> values) {
        return new ConditionNodes.InCondition(expression, "IN", values, false);
    }

    public static Condition notIn(SqlExpression expression, Collection<?> values) {
        return new ConditionNodes.InCondition(expression, "NOT IN", values, true);
    }

    public static Condition between(SqlExpression expression, Object start, Object end) {
        return new ConditionNodes.BetweenCondition(expression, start, end);
    }

    public static Condition eq(SqlExpression left, SqlExpression right) {
        return new ConditionNodes.ExpressionCondition(left, "=", right);
    }

    public static Condition ne(SqlExpression left, SqlExpression right) {
        return new ConditionNodes.ExpressionCondition(left, "<>", right);
    }

    public static Condition gt(SqlExpression left, SqlExpression right) {
        return new ConditionNodes.ExpressionCondition(left, ">", right);
    }

    public static Condition ge(SqlExpression left, SqlExpression right) {
        return new ConditionNodes.ExpressionCondition(left, ">=", right);
    }

    public static Condition lt(SqlExpression left, SqlExpression right) {
        return new ConditionNodes.ExpressionCondition(left, "<", right);
    }

    public static Condition le(SqlExpression left, SqlExpression right) {
        return new ConditionNodes.ExpressionCondition(left, "<=", right);
    }

    public static Condition group(Condition condition) {
        if (condition == null) {
            return null;
        }
        return new ConditionNodes.GroupCondition(condition);
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
        return new ConditionNodes.NotCondition(condition);
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
        return new ConditionNodes.CompositeCondition(operator, List.copyOf(filtered));
    }
}
