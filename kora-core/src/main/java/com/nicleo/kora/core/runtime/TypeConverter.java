package com.nicleo.kora.core.runtime;

public final class TypeConverter {
    private TypeConverter() {
    }

    public static String normalize(String name) {
        return name == null ? "" : name.replace("_", "").toLowerCase();
    }

    public static Object cast(Object value, Class<?> targetType) {
        if (value == null) {
            if (targetType.isPrimitive()) {
                throw new SqlSessionException("Cannot assign null to primitive type " + targetType.getName());
            }
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == int.class || targetType == Integer.class) {
            return ((Number) value).intValue();
        }
        if (targetType == long.class || targetType == Long.class) {
            return ((Number) value).longValue();
        }
        if (targetType == double.class || targetType == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (targetType == float.class || targetType == Float.class) {
            return ((Number) value).floatValue();
        }
        if (targetType == short.class || targetType == Short.class) {
            return ((Number) value).shortValue();
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return ((Number) value).byteValue();
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        return targetType.cast(value);
    }
}
