package com.nicleo.kora.core.runtime;

public interface GeneratedReflector<T> {
    T newInstance();

    Object invoke(T target, String method, Object[] args);

    void set(T target, String property, Object value);

    Object get(T target, String property);
}
