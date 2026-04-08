package com.nicleo.kora.core.annotation;

public enum ReflectMetadataLevel {
    NONE,
    FIELDS,
    METHODS,
    ALL;

    public boolean includesFields() {
        return this == FIELDS || this == ALL;
    }

    public boolean includesMethods() {
        return this == METHODS || this == ALL;
    }
}
