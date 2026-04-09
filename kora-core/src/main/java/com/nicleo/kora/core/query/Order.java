package com.nicleo.kora.core.query;

public record Order(String expression, boolean ascending) {
    public void appendTo(StringBuilder sql) {
        sql.append(expression).append(ascending ? " ASC" : " DESC");
    }
}
