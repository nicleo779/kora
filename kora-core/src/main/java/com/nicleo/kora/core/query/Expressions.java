package com.nicleo.kora.core.query;

public final class Expressions {
    private Expressions() {
    }

    public static SqlExpression raw(String value) {
        return new RawExpression(value);
    }

    public static SqlExpression aliasRef(String alias) {
        return new AliasReferenceExpression(alias);
    }

    public static SqlExpression literal(Object value) {
        return new LiteralExpression(value);
    }

    public static SqlExpression add(SqlExpression left, Object value) {
        return new ArithmeticExpression(left, "+", literal(value));
    }

    public static SqlExpression subtract(SqlExpression left, Object value) {
        return new ArithmeticExpression(left, "-", literal(value));
    }
}
