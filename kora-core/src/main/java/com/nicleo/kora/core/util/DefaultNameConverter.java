package com.nicleo.kora.core.util;

public final class DefaultNameConverter implements NameConverter {
    public static final DefaultNameConverter INSTANCE = new DefaultNameConverter();

    private DefaultNameConverter() {
    }

    @Override
    public String columnToField(Class<?> entityType, String columnName) {
        if (columnName == null || columnName.isEmpty()) {
            return columnName;
        }
        StringBuilder builder = new StringBuilder(columnName.length());
        boolean upperNext = false;
        for (int i = 0; i < columnName.length(); i++) {
            char current = columnName.charAt(i);
            if (current == '_') {
                upperNext = true;
                continue;
            }
            char normalized = Character.toLowerCase(current);
            if (upperNext) {
                builder.append(Character.toUpperCase(normalized));
                upperNext = false;
            } else {
                builder.append(normalized);
            }
        }
        return builder.toString();
    }

    @Override
    public String fieldToColumn(Class<?> entityType, String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return fieldName;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char current = fieldName.charAt(i);
            if (Character.isUpperCase(current)) {
                if (i > 0) {
                    builder.append('_');
                }
                builder.append(Character.toLowerCase(current));
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
