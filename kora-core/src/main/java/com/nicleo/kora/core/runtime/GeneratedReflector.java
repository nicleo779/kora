package com.nicleo.kora.core.runtime;

public interface GeneratedReflector<T> {
    T newInstance();

    Object invoke(T target, String method, Object[] args);

    void set(T target, String property, Object value);

    Object get(T target, String property);

    default FieldInfo[] getFields() {
        return new FieldInfo[0];
    }

    default FieldInfo getField(String field) {
        return null;
    }

    default MethodInfo[] getMethods() {
        return new MethodInfo[0];
    }

    default MethodInfo[] getMethod(String name) {
        return new MethodInfo[0];
    }
}
