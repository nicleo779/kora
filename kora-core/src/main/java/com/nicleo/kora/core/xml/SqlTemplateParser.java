package com.nicleo.kora.core.xml;

import com.nicleo.kora.core.runtime.BoundSql;

import java.util.ArrayList;
import java.util.List;

public final class SqlTemplateParser {
    private SqlTemplateParser() {
    }

    public static BoundSql parse(String rawSql) {
        StringBuilder sql = new StringBuilder();
        List<String> bindings = new ArrayList<>();
        int index = 0;
        while (index < rawSql.length()) {
            int start = rawSql.indexOf("#{", index);
            if (start < 0) {
                sql.append(rawSql.substring(index));
                break;
            }
            sql.append(rawSql, index, start);
            int end = rawSql.indexOf('}', start + 2);
            if (end < 0) {
                throw new XmlParseException("Unclosed placeholder in sql: " + rawSql);
            }
            String expression = rawSql.substring(start + 2, end).trim();
            if (expression.isEmpty()) {
                throw new XmlParseException("Empty placeholder in sql: " + rawSql);
            }
            bindings.add(expression);
            sql.append('?');
            index = end + 1;
        }
        return new BoundSql(normalizeWhitespace(sql.toString()), bindings);
    }

    private static String normalizeWhitespace(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}
