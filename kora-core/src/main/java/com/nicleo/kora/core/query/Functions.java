package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;
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
        return new FunctionExpression((sql, args, dbType) -> appendIf(sql, args, dbType, condition, whenTrue, whenFalse));
    }

    public static SqlExpression ifElse(Condition condition, Object whenTrue, Object whenFalse) {
        return ifElse(condition, Expressions.literal(whenTrue), Expressions.literal(whenFalse));
    }

    private static SqlExpression simple(String functionName, SqlExpression expression) {
        Objects.requireNonNull(expression, "expression");
        return new FunctionExpression((sql, args, dbType) -> {
            sql.append(functionName).append('(');
            expression.appendTo(sql, args, dbType);
            sql.append(')');
        });
    }

    private static void appendIf(StringBuilder sql,
                                 List<Object> args,
                                 DbType dbType,
                                 Condition condition,
                                 SqlExpression whenTrue,
                                 SqlExpression whenFalse) {
        switch (dbType) {
            case MYSQL, MARIADB -> {
                sql.append("IF(");
                condition.appendTo(sql, args, dbType);
                sql.append(", ");
                whenTrue.appendTo(sql, args, dbType);
                sql.append(", ");
                whenFalse.appendTo(sql, args, dbType);
                sql.append(')');
            }
            default -> {
                sql.append("CASE WHEN ");
                condition.appendTo(sql, args, dbType);
                sql.append(" THEN ");
                whenTrue.appendTo(sql, args, dbType);
                sql.append(" ELSE ");
                whenFalse.appendTo(sql, args, dbType);
                sql.append(" END");
            }
        }
    }
}
