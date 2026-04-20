package com.nicleo.kora.core.runtime;

import java.util.Map;

public record AnnotationMeta(String type, Map<String, Object> values) {
    public AnnotationMeta {
        values = values == null ? Map.of() : Map.copyOf(values);
    }

    public Object value(String name) {
        return values.get(name);
    }

    public String stringValue(String name) {
        Object raw = value(name);
        return raw == null ? null : String.valueOf(raw);
    }

    public Integer integerValue(String name) {
        Object raw = value(name);
        return switch (raw) {
            case null -> null;
            case Number number -> number.intValue();
            case String stringValue -> Integer.valueOf(stringValue);
            default -> throw unsupportedType(name, raw, "Integer");
        };
    }

    public Long longValue(String name) {
        Object raw = value(name);
        return switch (raw) {
            case null -> null;
            case Number number -> number.longValue();
            case String stringValue -> Long.valueOf(stringValue);
            default -> throw unsupportedType(name, raw, "Long");
        };
    }

    public Boolean booleanValue(String name) {
        Object raw = value(name);
        return switch (raw) {
            case null -> null;
            case Boolean booleanValue -> booleanValue;
            case String stringValue -> Boolean.valueOf(stringValue);
            default -> throw unsupportedType(name, raw, "Boolean");
        };
    }

    public Double doubleValue(String name) {
        Object raw = value(name);
        return switch (raw) {
            case null -> null;
            case Number number -> number.doubleValue();
            case String stringValue -> Double.valueOf(stringValue);
            default -> throw unsupportedType(name, raw, "Double");
        };
    }

    public String classValue(String name) {
        return stringValue(name);
    }

    private IllegalArgumentException unsupportedType(String name, Object raw, String targetType) {
        return new IllegalArgumentException("Annotation value '" + name + "' is not convertible to " + targetType + ": " + raw.getClass().getName());
    }
}
