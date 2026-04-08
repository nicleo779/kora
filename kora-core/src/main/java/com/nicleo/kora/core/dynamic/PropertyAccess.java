package com.nicleo.kora.core.dynamic;

import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.SqlSessionException;

import java.util.Map;

public final class PropertyAccess {
    private PropertyAccess() {
    }

    public static Object resolve(Map<String, Object> bindings, String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String[] parts = expression.split("\\.");
        String root = parts[0];
        if (bindings.containsKey(root)) {
            return resolvePath(bindings.get(root), parts, 1);
        }
        if (bindings.containsKey("_parameter")) {
            return resolveFromParameter(bindings.get("_parameter"), parts);
        }
        return null;
    }

    private static Object resolveFromParameter(Object parameter, String[] parts) {
        if (parameter == null) {
            return null;
        }
        if (parameter instanceof Map<?, ?> map) {
            Object value = map.get(parts[0]);
            return resolvePath(value, parts, 1);
        }
        if (isSimpleValue(parameter)) {
            return null;
        }
        return resolvePath(parameter, parts, 0);
    }

    private static Object resolvePath(Object current, String[] parts, int startIndex) {
        Object value = current;
        for (int i = startIndex; i < parts.length; i++) {
            value = getProperty(value, parts[i]);
        }
        return value;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Object getProperty(Object target, String property) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(property);
        }
        try {
            GeneratedReflector reflector = GeneratedReflectors.get((Class) target.getClass());
            return reflector.get(target, property);
        } catch (RuntimeException ex) {
            throw new SqlSessionException("Failed to resolve property '" + property + "' on type " + target.getClass().getName(), ex);
        }
    }

    private static boolean isSimpleValue(Object value) {
        Class<?> type = value.getClass();
        return type.isPrimitive()
                || value instanceof Number
                || value instanceof CharSequence
                || value instanceof Boolean
                || value instanceof Character
                || type.isEnum();
    }
}
