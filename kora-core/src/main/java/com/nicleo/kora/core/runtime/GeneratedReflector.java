package com.nicleo.kora.core.runtime;

public interface GeneratedReflector<T> {
    T newInstance();

    default ClassInfo getClassInfo() {
        return null;
    }

    Object invoke(T target, String method, Object[] args);

    void set(T target, String property, Object value);

    Object get(T target, String property);

    default String[] getFields() {
        return new String[0];
    }

    default boolean hasField(String field) {
        if (field == null) {
            return false;
        }
        return getField(field) != null;
    }

    default FieldInfo getField(String field) {
        return null;
    }

    default String[] getMethods() {
        return new String[0];
    }

    default boolean hasMethod(String name) {
        if (name == null) {
            return false;
        }
        return getMethod(name).length > 0;
    }

    default MethodInfo[] getMethod(String name) {
        return new MethodInfo[0];
    }
}
