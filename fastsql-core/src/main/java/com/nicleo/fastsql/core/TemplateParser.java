package com.nicleo.fastsql.core;

import java.util.ArrayList;
import java.util.List;

public final class TemplateParser {
    private final String source;
    private int index;

    private TemplateParser(String source) {
        this.source = source;
    }

    public static List<TemplateNode> parse(String source) {
        return new TemplateParser(source).parseNodes(false);
    }

    private List<TemplateNode> parseNodes(boolean stopAtBlockEnd) {
        List<TemplateNode> nodes = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        while (!isEnd()) {
            if (stopAtBlockEnd && peek() == '}') {
                break;
            }
            if (startsWith("${")) {
                flushText(nodes, text);
                nodes.add(new TemplateNode.ExprNode(readInterpolation()));
                continue;
            }
            if (startsWith("@if")) {
                flushText(nodes, text);
                nodes.add(readIf());
                continue;
            }
            if (startsWith("@for")) {
                flushText(nodes, text);
                nodes.add(readFor());
                continue;
            }
            text.append(advance());
        }
        flushText(nodes, text);
        return nodes;
    }

    private TemplateNode.IfNode readIf() {
        int start = index;
        expect("@if");
        skipWhitespace();
        String condition = readBalanced('(', ')');
        skipWhitespace();
        expect("{");
        List<TemplateNode> body = parseNodes(true);
        if (isEnd() || peek() != '}') {
            throw new TemplateParseException("Missing closing '}' for @if block", start);
        }
        advance();
        return new TemplateNode.IfNode(condition, body);
    }

    private TemplateNode.ForNode readFor() {
        int start = index;
        expect("@for");
        skipWhitespace();
        String header = readBalanced('(', ')');
        skipWhitespace();
        expect("{");
        List<TemplateNode> body = parseNodes(true);
        if (isEnd() || peek() != '}') {
            throw new TemplateParseException("Missing closing '}' for @for block", start);
        }
        advance();
        return new TemplateNode.ForNode(header, body);
    }

    private String readInterpolation() {
        int start = index;
        expect("${");
        String expression = readBalancedContent('{', '}');
        if (expression.isBlank()) {
            throw new TemplateParseException("Empty interpolation expression", start);
        }
        return expression.trim();
    }

    private String readBalanced(char open, char close) {
        if (isEnd() || peek() != open) {
            throw new TemplateParseException("Expected '" + open + "'", index);
        }
        advance();
        return readBalancedContent(open, close).trim();
    }

    private String readBalancedContent(char open, char close) {
        StringBuilder sb = new StringBuilder();
        int depth = 1;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        while (!isEnd()) {
            char ch = advance();
            if (ch == '\\' && !isEnd()) {
                sb.append(ch).append(advance());
                continue;
            }
            if (!inDoubleQuote && ch == '\'') {
                inSingleQuote = !inSingleQuote;
                sb.append(ch);
                continue;
            }
            if (!inSingleQuote && ch == '"') {
                inDoubleQuote = !inDoubleQuote;
                sb.append(ch);
                continue;
            }
            if (!inSingleQuote && !inDoubleQuote) {
                if (ch == open) {
                    depth++;
                } else if (ch == close) {
                    depth--;
                    if (depth == 0) {
                        return sb.toString();
                    }
                }
            }
            sb.append(ch);
        }
        throw new TemplateParseException("Unclosed '" + open + "'", index);
    }

    private void flushText(List<TemplateNode> nodes, StringBuilder text) {
        if (!text.isEmpty()) {
            nodes.add(new TemplateNode.TextNode(text.toString()));
            text.setLength(0);
        }
    }

    private void skipWhitespace() {
        while (!isEnd() && Character.isWhitespace(peek())) {
            advance();
        }
    }

    private void expect(String expected) {
        if (!startsWith(expected)) {
            throw new TemplateParseException("Expected \"" + expected + "\"", index);
        }
        index += expected.length();
    }

    private boolean startsWith(String expected) {
        return source.startsWith(expected, index);
    }

    private char peek() {
        return source.charAt(index);
    }

    private char advance() {
        return source.charAt(index++);
    }

    private boolean isEnd() {
        return index >= source.length();
    }
}
