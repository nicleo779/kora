package com.nicleo.kora.core.dynamic;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MapperParameters {
    private MapperParameters() {
    }

    public static Map<String, Object> build(String[] names, Object[] values) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        if (names.length == 1) {
            Object only = values[0];
            parameters.put("_parameter", only);
            parameters.put("value", only);
            if (only instanceof Iterable<?>) {
                parameters.put("collection", only);
                parameters.put("list", only);
            } else if (only != null && only.getClass().isArray()) {
                parameters.put("array", only);
                parameters.put("collection", only);
            }
        }
        for (int i = 0; i < names.length; i++) {
            parameters.put(names[i], values[i]);
            parameters.put("arg" + i, values[i]);
            parameters.put("param" + (i + 1), values[i]);
        }
        if (!parameters.containsKey("_parameter")) {
            parameters.put("_parameter", parameters);
        }
        return parameters;
    }
}
