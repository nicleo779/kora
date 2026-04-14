package com.nicleo.kora.core.dynamic;

public final class IfSqlNode implements DynamicSqlNode {
    private final String test;
    private final DynamicSqlNode contents;

    public IfSqlNode(String test, DynamicSqlNode contents) {
        this.test = test;
        this.contents = contents;
    }

    public String getTest() {
        return test;
    }

    public DynamicSqlNode getContents() {
        return contents;
    }

    @Override
    public String render(DynamicSqlContext context) {
        return context.evaluateBoolean(test) ? contents.render(context) : "";
    }
}
