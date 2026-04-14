package com.nicleo.kora.core.runtime;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class BoundSql {
    private final String sql;
    private final List<String> bindings;
    private final Map<String, Object> values;

    public BoundSql(String sql, List<String> bindings) {
        this(sql, bindings, Map.of());
    }

    public BoundSql(String sql, List<String> bindings, Map<String, Object> values) {
        this.sql = sql;
        this.bindings = List.copyOf(bindings);
        this.values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    public String getSql() {
        return sql;
    }

    public List<String> getBindings() {
        return bindings;
    }

    public Map<String, Object> getValues() {
        return values;
    }
}
