package com.nicleo.kora.core.runtime;

@FunctionalInterface
public interface CustomTypeConverter<T> {
    T convert(Object value);
}
