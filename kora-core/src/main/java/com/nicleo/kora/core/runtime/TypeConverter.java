package com.nicleo.kora.core.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TypeConverter {
    private final List<CustomTypeConverter> customConverters = new CopyOnWriteArrayList<>();

    public TypeConverter register(CustomTypeConverter converter) {
        customConverters.add(converter);
        return this;
    }

    public void clearCustomConverters() {
        customConverters.clear();
    }

    public Object cast(Object value, Class<?> targetType) {
        Class<?> normalizedTargetType = wrap(targetType);
        if (value == null) {
            if (normalizedTargetType != targetType || targetType.isPrimitive()) {
                throw new SqlSessionException("Cannot assign null to primitive type " + targetType.getName());
            }
            return null;
        }
        if (normalizedTargetType.isInstance(value)) {
            return value;
        }
        Object converted = applyCustomConvertersFromDb(value, normalizedTargetType);
        if (converted != null) {
            return converted;
        }
        if (normalizedTargetType == Integer.class) {
            return ((Number) value).intValue();
        }
        if (normalizedTargetType == Long.class) {
            return ((Number) value).longValue();
        }
        if (normalizedTargetType == Double.class) {
            return ((Number) value).doubleValue();
        }
        if (normalizedTargetType == Float.class) {
            return ((Number) value).floatValue();
        }
        if (normalizedTargetType == Short.class) {
            return ((Number) value).shortValue();
        }
        if (normalizedTargetType == Byte.class) {
            return ((Number) value).byteValue();
        }
        if (normalizedTargetType == Boolean.class) {
            if (value instanceof Boolean booleanValue) {
                return booleanValue;
            }
            if (value instanceof Number number) {
                return number.intValue() != 0;
            }
        }
        if (normalizedTargetType == String.class) {
            return String.valueOf(value);
        }
        return normalizedTargetType.cast(value);
    }

    public Object toDbValue(Object value) {
        if (value == null) {
            return null;
        }
        Object converted = applyCustomConvertersToDb(value, wrap(value.getClass()));
        return converted != null ? converted : value;
    }

    private Object applyCustomConvertersFromDb(Object value, Class<?> targetType) {
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(targetType)) {
                return converter.fromDb(value, targetType);
            }
        }
        return null;
    }

    private Object applyCustomConvertersToDb(Object value, Class<?> sourceType) {
        for (CustomTypeConverter converter : customConverters) {
            if (converter.supports(sourceType)) {
                return converter.toDb(value, sourceType);
            }
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        return switch (type.getName()) {
            case "boolean" -> Boolean.class;
            case "byte" -> Byte.class;
            case "short" -> Short.class;
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "float" -> Float.class;
            case "double" -> Double.class;
            case "char" -> Character.class;
            default -> type;
        };
    }

}
