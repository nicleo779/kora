package com.nicleo.fastsql.idea;

import com.intellij.psi.PsiLiteralExpression;

final class TemplateContext {
    enum Kind {
        TEMPLATE,
        JAVA_EXPRESSION,
        JAVA_STATEMENT_HEADER
    }

    private final PsiLiteralExpression literal;
    private final int templateOffset;
    private final Kind kind;
    private final int javaStartOffset;
    private final String javaPrefix;

    private TemplateContext(PsiLiteralExpression literal, int templateOffset, Kind kind, int javaStartOffset, String javaPrefix) {
        this.literal = literal;
        this.templateOffset = templateOffset;
        this.kind = kind;
        this.javaStartOffset = javaStartOffset;
        this.javaPrefix = javaPrefix;
    }

    static TemplateContext at(PsiLiteralExpression literal, int templateOffset) {
        String text = FastSqlPsiUtil.getTemplateText(literal);
        if (text == null || templateOffset < 0 || templateOffset > text.length()) {
            return null;
        }
        int exprStart = text.lastIndexOf("${", templateOffset);
        int exprEnd = exprStart >= 0 ? text.indexOf('}', exprStart + 2) : -1;
        if (exprStart >= 0 && exprEnd >= templateOffset) {
            return new TemplateContext(literal, templateOffset, Kind.JAVA_EXPRESSION, exprStart + 2, text.substring(exprStart + 2, templateOffset));
        }
        int ifStart = text.lastIndexOf("@if", templateOffset);
        int forStart = text.lastIndexOf("@for", templateOffset);
        int directiveStart = Math.max(ifStart, forStart);
        if (directiveStart >= 0) {
            int parenStart = text.indexOf('(', directiveStart);
            int parenEnd = text.indexOf(')', parenStart);
            if (parenStart >= 0 && parenStart < templateOffset && (parenEnd < 0 || parenEnd >= templateOffset)) {
                return new TemplateContext(literal, templateOffset, Kind.JAVA_STATEMENT_HEADER, parenStart + 1, text.substring(parenStart + 1, templateOffset));
            }
        }
        return new TemplateContext(literal, templateOffset, Kind.TEMPLATE, -1, "");
    }

    PsiLiteralExpression literal() {
        return literal;
    }

    int templateOffset() {
        return templateOffset;
    }

    Kind kind() {
        return kind;
    }

    int javaStartOffset() {
        return javaStartOffset;
    }

    String javaPrefix() {
        return javaPrefix;
    }
}
