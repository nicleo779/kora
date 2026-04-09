package com.nicleo.kora.core.runtime;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class TypeConverter {
    private final List<RegisteredConverter<?>> customConverters = new CopyOnWriteArrayList<>();

    public <T> void register(Class<T> targetType, CustomTypeConverter<? extends T> converter) {
        customConverters.add(registeredConverter(targetType, converter));
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
        Object converted = applyCustomConverters(value, normalizedTargetType);
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

    private Object applyCustomConverters(Object value, Class<?> targetType) {
        for (RegisteredConverter<?> registeredConverter : customConverters) {
            if (registeredConverter.supports(targetType)) {
                return registeredConverter.convert(value);
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

    @SuppressWarnings("unchecked")
    private static <T> RegisteredConverter<T> registeredConverter(Class<T> targetType, CustomTypeConverter<? extends T> converter) {
        return new RegisteredConverter<>((Class<T>) wrap(targetType), converter);
    }

    private static final class RegisteredConverter<T> {
        private final Class<T> targetType;
        private final CustomTypeConverter<? extends T> converter;

        private RegisteredConverter(Class<T> targetType, CustomTypeConverter<? extends T> converter) {
            this.targetType = targetType;
            this.converter = converter;
        }

        private boolean supports(Class<?> targetType) {
            return this.targetType == targetType;
        }

        private Object convert(Object value) {
            return converter.convert(value);
        }
    }
}
