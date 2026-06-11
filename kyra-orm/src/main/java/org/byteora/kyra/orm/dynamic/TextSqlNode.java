package org.byteora.kyra.orm.dynamic;

import java.util.ArrayList;
import java.util.List;

public final class TextSqlNode implements DynamicSqlNode {
    private final String text;
    private final List<Segment> segments;

    public TextSqlNode(String text) {
        this.text = text;
        this.segments = compile(text);
    }

    public String getText() {
        return text;
    }

    @Override
    public String render(DynamicSqlContext context) {
        if (segments.size() == 1 && segments.get(0) instanceof LiteralSegment literal) {
            return literal.value();
        }
        StringBuilder sql = new StringBuilder();
        for (Segment segment : segments) {
            segment.appendTo(sql, context);
        }
        return sql.toString();
    }

    /**
     * Splits the raw text once (at node construction) into literal runs, {@code #{}} placeholders and
     * {@code ${}} substitutions. {@code ${}} expressions are compiled here so each render only walks
     * the segment list instead of re-scanning the text and re-parsing the expression every call.
     * {@code #{}} expressions keep their source form because the foreach-scope alias is only known at
     * render time.
     */
    private static List<Segment> compile(String text) {
        List<Segment> segments = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            int hash = text.indexOf("#{", index);
            int dollar = text.indexOf("${", index);
            int start = nextTokenStart(hash, dollar);
            if (start < 0) {
                segments.add(new LiteralSegment(text.substring(index)));
                break;
            }
            if (start > index) {
                segments.add(new LiteralSegment(text.substring(index, start)));
            }
            boolean hashToken = start == hash;
            int end = text.indexOf('}', start + 2);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed placeholder in sql segment: " + text);
            }
            String expression = text.substring(start + 2, end).trim();
            if (hashToken) {
                segments.add(new HashSegment(expression));
            } else {
                segments.add(new DollarSegment(ExpressionEvaluator.compile(expression)));
            }
            index = end + 1;
        }
        if (segments.isEmpty()) {
            segments.add(new LiteralSegment(""));
        }
        return List.copyOf(segments);
    }

    private static int nextTokenStart(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private sealed interface Segment permits LiteralSegment, HashSegment, DollarSegment {
        void appendTo(StringBuilder sql, DynamicSqlContext context);
    }

    private record LiteralSegment(String value) implements Segment {
        @Override
        public void appendTo(StringBuilder sql, DynamicSqlContext context) {
            sql.append(value);
        }
    }

    private record HashSegment(String expression) implements Segment {
        @Override
        public void appendTo(StringBuilder sql, DynamicSqlContext context) {
            sql.append("#{").append(context.aliasExpression(expression)).append('}');
        }
    }

    private record DollarSegment(CompiledExpression expression) implements Segment {
        @Override
        public void appendTo(StringBuilder sql, DynamicSqlContext context) {
            Object value = context.evaluateValue(expression);
            sql.append(value == null ? "" : value);
        }
    }
}
