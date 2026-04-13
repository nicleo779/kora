package com.nicleo.kora.core.dynamic;

public final class TextSqlNode implements DynamicSqlNode {
    private final String text;

    public TextSqlNode(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    @Override
    public String render(DynamicSqlContext context) {
        return context.applyText(text);
    }
}
