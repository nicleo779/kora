package org.byteora.kyra.orm.runtime;

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

    private BoundSql(String sql, List<String> bindings, Map<String, Object> values, boolean trusted) {
        this.sql = sql;
        this.bindings = bindings;
        this.values = values;
    }

    /**
     * Adopts the given {@code bindings} and {@code values} without defensive copying. Intended for the
     * internal render hot path, where both arguments are freshly built per call and only read
     * afterwards. Callers MUST NOT mutate either argument after handing it over.
     */
    public static BoundSql ofTrusted(String sql, List<String> bindings, Map<String, Object> values) {
        return new BoundSql(sql, bindings, values, true);
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
