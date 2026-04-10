package com.nicleo.kora.core.query;

public final class Expressions {
    private Expressions() {
    }

    public static SqlExpression raw(String value) {
        return new RawExpression(value);
    }

    public static SqlExpression literal(Object value) {
        return new LiteralExpression(value);
    }
}
