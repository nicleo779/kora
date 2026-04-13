package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.TypeConverter;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class GeneratedRowMapper<T> implements RowMapper<T> {
    private final GeneratedReflector<T> reflector;
    private final TypeConverter typeConverter;
    private final Map<String, String> columnToField;

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector) {
        this(entityType, reflector, new TypeConverter());
    }

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector, TypeConverter typeConverter) {
        Objects.requireNonNull(entityType, "entityType");
        this.reflector = Objects.requireNonNull(reflector, "reflector");
        this.typeConverter = Objects.requireNonNull(typeConverter, "typeConverter");
        this.columnToField = buildColumnToFieldMap(reflector);
    }

    @Override
    public T mapRow(ResultSet resultSet) throws SQLException {
        T instance = reflector.newInstance();
        ResultSetMetaData metaData = resultSet.getMetaData();
        for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
            String columnLabel = metaData.getColumnLabel(columnIndex);
            Object value = resultSet.getObject(columnIndex);
            String fieldName = resolveFieldName(columnLabel);
            FieldInfo fieldInfo = reflector.getField(fieldName);
            if (fieldInfo == null) {
                continue;
            }
            Class<?> targetType = resolveTargetType(fieldInfo.type());
            if (targetType != null) {
                value = typeConverter.cast(value, targetType);
            }
            reflector.set(instance, fieldName, value);
        }
        return instance;
    }

    private String resolveFieldName(String columnLabel) {
        if (reflector.hasField(columnLabel)) {
            return columnLabel;
        }
        String mappedField = columnToField.get(normalizeColumnLabel(columnLabel));
        if (mappedField != null) {
            return mappedField;
        }
        return snakeToCamel(columnLabel);
    }

    private Class<?> resolveTargetType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> rawType) {
            return rawType;
        }
        return null;
    }

    private Map<String, String> buildColumnToFieldMap(GeneratedReflector<T> reflector) {
        Map<String, String> mapping = new HashMap<>();
        for (String fieldName : reflector.fieldNamesView()) {
            FieldInfo fieldInfo = reflector.getField(fieldName);
            if (fieldInfo == null) {
                continue;
            }
            mapping.putIfAbsent(normalizeColumnLabel(fieldName), fieldName);
            if (fieldInfo.alias() != null && !fieldInfo.alias().isBlank()) {
                mapping.put(normalizeColumnLabel(fieldInfo.alias()), fieldName);
            }
        }
        return mapping;
    }

    private String normalizeColumnLabel(String columnLabel) {
        return columnLabel == null ? null : columnLabel.toLowerCase(Locale.ROOT);
    }

    private String snakeToCamel(String columnLabel) {
        if (columnLabel == null || columnLabel.isEmpty()) {
            return columnLabel;
        }
        String normalized = columnLabel.toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder(normalized.length());
        boolean upperNext = false;
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (current == '_') {
                upperNext = true;
                continue;
            }
            if (upperNext) {
                builder.append(Character.toUpperCase(current));
                upperNext = false;
            } else {
                builder.append(current);
            }
        }
        return builder.toString();
    }
}
