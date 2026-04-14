package com.nicleo.kora.core.dynamic;

public interface DynamicSqlNode {
    String render(DynamicSqlContext context);
}
