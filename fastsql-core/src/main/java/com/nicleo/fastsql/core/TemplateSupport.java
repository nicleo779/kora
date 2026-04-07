package com.nicleo.fastsql.core;

public final class TemplateSupport {
    private TemplateSupport() {
    }

    public static boolean hasTemplateSyntax(String text) {
        return text != null && (text.contains("@if") || text.contains("@for") || text.contains("${"));
    }

    public static boolean isTemplateEditingCandidate(String text) {
        return text != null && (hasTemplateSyntax(text) || text.contains("@") || text.contains("${"));
    }
}
