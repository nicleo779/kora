package com.nicleo.kora.core.runtime;

import java.util.Map;

public record AnnotationMeta(String type, Map<String, Object> values) {
    public AnnotationMeta {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public Object value(String name) {
        return values.get(name);
    }
}
