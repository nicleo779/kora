package com.nicleo.kora.core.runtime;

public interface GeneratedReflector<T> {

    T newInstance();

    default T newInstance(Object[] args) {
        throw new UnsupportedOperationException("Type does not support constructor instantiation");
    }

    ClassInfo getClassInfo();

    Object invoke(T target, int index, Object[] args);

    default Object invoke(T target, String method, Object[] args) {
        int index = methodIndex(method);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown method");
        }
        return invoke(target, index, args);
    }

    void set(T target, int index, Object value);

    default void set(T target, String property, Object value) {
        int index = fieldIndex(property);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown property: " + property);
        }
        set(target, index, value);
    }

    Object get(T target, int index);

    default Object get(T target, String property) {
        int index = fieldIndex(property);
        if (index < 0) {
            throw new IllegalArgumentException("Unknown property: " + property);
        }
        return get(target, index);
    }

    String[] getFields();

    default boolean hasField(String field) {
        return fieldIndex(field) >= 0;
    }

    FieldInfo getField(int index);

    default FieldInfo getField(String field) {
        int index = fieldIndex(field);
        return index < 0 ? null : getField(index);
    }

    String[] getMethods();

    default boolean hasMethod(String name) {
        return methodIndex(name) >= 0;
    }

    int getMethod(int index);

    MethodInfo[] getMethod(String name);

    default int fieldIndex(String property) {
        if (property == null) {
            return -1;
        }
        String[] fields = getFields();
        for (int i = 0; i < fields.length; i++) {
            if (property.equals(fields[i])) {
                return i;
            }
        }
        return -1;
    }

    default int methodIndex(String method) {
        if (method == null) {
            return -1;
        }
        String[] methods = getMethods();
        for (int i = 0; i < methods.length; i++) {
            if (method.equals(methods[i])) {
                return i;
            }
        }
        return -1;
    }
}
