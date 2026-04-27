package com.nicleo.kora.core.dynamic;

import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.SqlExecutorException;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Objects;
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
        if (property == null || property.isBlank()) {
            return null;
        }
        if (property.endsWith("()")) {
            return invoke(target, property.substring(0, property.length() - 2), null);
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(property);
        }
        try {
            GeneratedReflector reflector = GeneratedReflectors.get((Class) target.getClass());
            return reflector.get(target, property);
        } catch (RuntimeException ex) {
            throw new SqlExecutorException("Failed to resolve property '" + property + "' on type " + target.getClass().getName(), ex);
        }
    }

    static Object invoke(Object target, String methodName, Object argument) {
        if (target == null) {
            return null;
        }
        if (argument == null) {
            return invokeZeroArg(target, methodName);
        }
        return invokeSingleArg(target, methodName, argument);
    }

    private static Object invokeZeroArg(Object target, String methodName) {
        return switch (methodName) {
            case "size" -> sizeOf(target);
            case "length" -> sizeOf(target);
            case "isEmpty" -> isEmpty(target);
            case "ordinal" -> ordinalOf(target);
            case "name" -> nameOf(target);
            default -> throw new SqlExecutorException("Unsupported zero-arg method '" + methodName + "()' on type " + target.getClass().getName());
        };
    }

    private static Object invokeSingleArg(Object target, String methodName, Object argument) {
        return switch (methodName) {
            case "contains" -> contains(target, argument);
            default -> throw new SqlExecutorException("Unsupported single-arg method '" + methodName + "(...)' on type " + target.getClass().getName());
        };
    }

    private static int sizeOf(Object target) {
        if (target instanceof Collection<?> collection) {
            return collection.size();
        }
        if (target instanceof Map<?, ?> map) {
            return map.size();
        }
        if (target instanceof CharSequence sequence) {
            return sequence.length();
        }
        if (target.getClass().isArray()) {
            return Array.getLength(target);
        }
        throw new SqlExecutorException("Unsupported size() on type " + target.getClass().getName());
    }

    private static boolean isEmpty(Object target) {
        if (target instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (target instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        if (target instanceof CharSequence sequence) {
            return sequence.isEmpty();
        }
        if (target.getClass().isArray()) {
            return Array.getLength(target) == 0;
        }
        throw new SqlExecutorException("Unsupported isEmpty() on type " + target.getClass().getName());
    }

    private static boolean contains(Object target, Object argument) {
        if (target instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (matches(item, argument)) {
                    return true;
                }
            }
            return false;
        }
        if (target instanceof Map<?, ?> map) {
            return map.containsKey(argument);
        }
        if (target instanceof CharSequence sequence) {
            return sequence.toString().contains(String.valueOf(argument));
        }
        if (target.getClass().isArray()) {
            int length = Array.getLength(target);
            for (int i = 0; i < length; i++) {
                if (Objects.equals(Array.get(target, i), argument)) {
                    return true;
                }
            }
            return false;
        }
        throw new SqlExecutorException("Unsupported contains(...) on type " + target.getClass().getName());
    }

    private static boolean matches(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private static int ordinalOf(Object target) {
        if (target instanceof Enum<?> enumValue) {
            return enumValue.ordinal();
        }
        throw new SqlExecutorException("Unsupported ordinal() on type " + target.getClass().getName());
    }

    private static String nameOf(Object target) {
        if (target instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        throw new SqlExecutorException("Unsupported name() on type " + target.getClass().getName());
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
