package org.byteora.kyra.orm.dynamic;

public final class WhenSqlNode {
    private final String test;
    private final CompiledExpression compiledTest;
    private final DynamicSqlNode contents;

    public WhenSqlNode(String test, DynamicSqlNode contents) {
        this.test = test;
        this.compiledTest = ExpressionEvaluator.compile(test);
        this.contents = contents;
    }

    public String getTest() {
        return test;
    }

    CompiledExpression getCompiledTest() {
        return compiledTest;
    }

    public DynamicSqlNode getContents() {
        return contents;
    }
}
