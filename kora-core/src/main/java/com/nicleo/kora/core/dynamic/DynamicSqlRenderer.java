package com.nicleo.kora.core.dynamic;

import com.nicleo.kora.core.runtime.BoundSql;
import com.nicleo.kora.core.xml.SqlTemplateParser;

import java.util.Map;

public final class DynamicSqlRenderer {
    private DynamicSqlRenderer() {
    }

    public static BoundSql render(DynamicSqlNode root, Map<String, Object> bindings) {
        DynamicSqlContext context = new DynamicSqlContext(bindings);
        String rawSql = root.render(context);
        BoundSql parsed = SqlTemplateParser.parse(rawSql);
        return new BoundSql(parsed.getSql(), parsed.getBindings(), context.getBindings());
    }
}
