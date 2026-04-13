package com.nicleo.kora.core.dynamic;

public final class BindSqlNode implements DynamicSqlNode {
    private final String name;
    private final String valueExpression;

    public BindSqlNode(String name, String valueExpression) {
        this.name = name;
        this.valueExpression = valueExpression;
    }

    public String getName() {
        return name;
    }

    public String getValueExpression() {
        return valueExpression;
    }

    @Override
    public String render(DynamicSqlContext context) {
        context.bind(name, context.evaluateValue(valueExpression));
        return "";
    }
}
