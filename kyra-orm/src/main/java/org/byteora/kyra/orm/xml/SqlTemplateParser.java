package org.byteora.kyra.orm.xml;

import org.byteora.kyra.orm.runtime.BoundSql;

import java.util.ArrayList;
import java.util.List;

public final class SqlTemplateParser {
    private SqlTemplateParser() {
    }

    /**
     * Extracts {@code #{}} placeholders into ordered binding expressions and collapses whitespace in a
     * single pass. Whitespace runs become one space and the leading/trailing space is trimmed, which
     * matches the previous {@code replaceAll("\\s+", " ").trim()} step but without recompiling a regex
     * on every call.
     */
    public static BoundSql parse(String rawSql) {
        int length = rawSql.length();
        StringBuilder sql = new StringBuilder(length);
        List<String> bindings = new ArrayList<>();
        boolean pendingSpace = false;
        boolean started = false;
        int index = 0;
        while (index < length) {
            char ch = rawSql.charAt(index);
            if (ch == '#' && index + 1 < length && rawSql.charAt(index + 1) == '{') {
                int end = rawSql.indexOf('}', index + 2);
                if (end < 0) {
                    throw new XmlParseException("Unclosed placeholder in sql: " + rawSql);
                }
                String expression = rawSql.substring(index + 2, end).trim();
                if (expression.isEmpty()) {
                    throw new XmlParseException("Empty placeholder in sql: " + rawSql);
                }
                if (pendingSpace && started) {
                    sql.append(' ');
                }
                pendingSpace = false;
                bindings.add(expression);
                sql.append('?');
                started = true;
                index = end + 1;
                continue;
            }
            if (ch == ' ' || ch == '\t' || ch == '\n' || ch == '\r' || ch == '\f') {
                pendingSpace = true;
                index++;
                continue;
            }
            if (pendingSpace && started) {
                sql.append(' ');
            }
            pendingSpace = false;
            sql.append(ch);
            started = true;
            index++;
        }
        return new BoundSql(sql.toString(), bindings);
    }
}
