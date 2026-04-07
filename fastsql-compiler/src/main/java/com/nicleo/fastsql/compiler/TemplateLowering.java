package com.nicleo.fastsql.compiler;

import com.nicleo.fastsql.core.TemplateNode;

import java.util.List;

final class TemplateLowering {
    private TemplateLowering() {
    }

    static String toSupplierInvocation(String template) {
        List<TemplateNode> nodes = com.nicleo.fastsql.core.TemplateParser.parse(template);
        StringBuilder code = new StringBuilder();
        code.append("((java.util.function.Supplier<String>) () -> {");
        code.append("java.lang.StringBuilder __sb = new java.lang.StringBuilder();");
        appendNodes(nodes, code);
        code.append("return __sb.toString();");
        code.append("}).get()");
        return code.toString();
    }

    private static void appendNodes(List<TemplateNode> nodes, StringBuilder code) {
        for (TemplateNode node : nodes) {
            if (node instanceof TemplateNode.TextNode textNode) {
                if (!textNode.text().isEmpty()) {
                    code.append("__sb.append(").append(toJavaStringLiteral(textNode.text())).append(");");
                }
            } else if (node instanceof TemplateNode.ExprNode exprNode) {
                code.append("__sb.append(java.lang.String.valueOf(").append(exprNode.expression()).append("));");
            } else if (node instanceof TemplateNode.IfNode ifNode) {
                code.append("if (").append(ifNode.condition()).append(") {");
                appendNodes(ifNode.body(), code);
                code.append("}");
            } else if (node instanceof TemplateNode.ForNode forNode) {
                code.append("for (").append(forNode.header()).append(") {");
                appendNodes(forNode.body(), code);
                code.append("}");
            }
        }
    }

    private static String toJavaStringLiteral(String text) {
        StringBuilder escaped = new StringBuilder("\"");
        for (char ch : text.toCharArray()) {
            switch (ch) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(ch);
            }
        }
        escaped.append('"');
        return escaped.toString();
    }
}
