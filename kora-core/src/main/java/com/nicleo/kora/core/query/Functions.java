package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.dialect.RenderContext;
import java.util.Objects;

public final class Functions {
    private static final SqlExpression STAR = Expressions.raw("*");

    private Functions() {
    }

    public static SqlExpression count() {
        return count(STAR);
    }

    public static SqlExpression count(SqlExpression expression) {
        return simple("COUNT", expression);
    }

    public static SqlExpression avg(SqlExpression expression) {
        return simple("AVG", expression);
    }

    public static SqlExpression max(SqlExpression expression) {
        return simple("MAX", expression);
    }

    public static SqlExpression min(SqlExpression expression) {
        return simple("MIN", expression);
    }

    public static SqlExpression ifElse(Condition condition, SqlExpression whenTrue, SqlExpression whenFalse) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(whenTrue, "whenTrue");
        Objects.requireNonNull(whenFalse, "whenFalse");
        return new FunctionExpression(context -> appendIf(context, condition, whenTrue, whenFalse));
    }

    public static SqlExpression ifElse(Condition condition, Object whenTrue, Object whenFalse) {
        return ifElse(condition, Expressions.literal(whenTrue), Expressions.literal(whenFalse));
    }

    private static SqlExpression simple(String functionName, SqlExpression expression) {
        Objects.requireNonNull(expression, "expression");
        return new FunctionExpression(context -> {
            context.sql().append(functionName).append('(');
            expression.appendTo(context);
            context.sql().append(')');
        });
    }

    private static void appendIf(RenderContext context,
                                 Condition condition,
                                 SqlExpression whenTrue,
                                 SqlExpression whenFalse) {
        switch (context.dialect().dbType()) {
            case MYSQL, MARIADB -> {
                context.sql().append("IF(");
                condition.appendTo(context);
                context.sql().append(", ");
                whenTrue.appendTo(context);
                context.sql().append(", ");
                whenFalse.appendTo(context);
                context.sql().append(')');
            }
            default -> {
                context.sql().append("CASE WHEN ");
                condition.appendTo(context);
                context.sql().append(" THEN ");
                whenTrue.appendTo(context);
                context.sql().append(" ELSE ");
                whenFalse.appendTo(context);
                context.sql().append(" END");
            }
        }
    }
}
