package org.byteora.kyra.orm.dynamic;

public final class BindSqlNode implements DynamicSqlNode {
    private final String name;
    private final String valueExpression;
    private final CompiledExpression compiledValue;

    public BindSqlNode(String name, String valueExpression) {
        this.name = name;
        this.valueExpression = valueExpression;
        this.compiledValue = ExpressionEvaluator.compile(valueExpression);
    }

    public String getName() {
        return name;
    }

    public String getValueExpression() {
        return valueExpression;
    }

    @Override
    public String render(DynamicSqlContext context) {
        context.bind(name, context.evaluateValue(compiledValue));
        return "";
    }
}
