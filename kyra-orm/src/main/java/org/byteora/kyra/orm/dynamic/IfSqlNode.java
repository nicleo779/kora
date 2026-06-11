package org.byteora.kyra.orm.dynamic;

public final class IfSqlNode implements DynamicSqlNode {
    private final String test;
    private final CompiledExpression compiledTest;
    private final DynamicSqlNode contents;

    public IfSqlNode(String test, DynamicSqlNode contents) {
        this.test = test;
        this.compiledTest = ExpressionEvaluator.compile(test);
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
        return context.evaluateBoolean(compiledTest) ? contents.render(context) : "";
    }
}
