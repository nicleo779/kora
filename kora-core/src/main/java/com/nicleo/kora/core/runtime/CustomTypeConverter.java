package com.nicleo.kora.core.runtime;

public interface CustomTypeConverter {
    boolean supports(Class<?> targetType);

    Object fromDb(Object value, Class<?> targetType);

    Object toDb(Object value, Class<?> targetType);
}
