package com.nicleo.kora.core.util;

public interface NameConverter {
    String columnToField(Class<?> entityType, String columnName);

    String fieldToColumn(Class<?> entityType, String fieldName);
}
