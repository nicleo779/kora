package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.ParameterInfo;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.SqlExecutorException;
import com.nicleo.kora.core.runtime.TypeConverter;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class GeneratedRowMapper<T> implements RowMapper<T> {
    private final Class<T> entityType;
    private final GeneratedReflector<T> reflector;
    private final TypeConverter typeConverter;
    private final Map<String, String> columnToField;

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector) {
        this(entityType, reflector, new TypeConverter());
    }

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector, TypeConverter typeConverter) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.reflector = Objects.requireNonNull(reflector, "reflector");
        this.typeConverter = Objects.requireNonNull(typeConverter, "typeConverter");
        this.columnToField = buildColumnToFieldMap(reflector);
    }

    @Override
    public T mapRow(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        Map<String, Object> values = readColumnValues(resultSet, metaData);
        ParameterInfo[] params = constructorParams();
        if (params.length == 0) {
            T instance = reflector.newInstance();
            applyValues(instance, values, Set.of());
            return instance;
        }
        T instance = newInstanceFromConstructor(values, params);
        if (!entityType.isRecord()) {
            applyValues(instance, values, constructorParamNames(params));
        }
        return instance;
    }

    private Map<String, Object> readColumnValues(ResultSet resultSet, ResultSetMetaData metaData) throws SQLException {
        Map<String, Object> values = new HashMap<>();
        for (int columnIndex = 1; columnIndex <= metaData.getColumnCount(); columnIndex++) {
            String columnLabel = metaData.getColumnLabel(columnIndex);
            String fieldName = resolveFieldName(columnLabel);
            FieldInfo fieldInfo = reflector.getField(fieldName);
            if (fieldInfo == null) {
                continue;
            }
            Class<?> targetType = resolveTargetType(fieldInfo.type());
            Object value;
            if (targetType != null) {
                value = typeConverter.cast(resultSet, columnIndex, targetType, columnLabel, fieldName);
            } else {
                value = resultSet.getObject(columnIndex);
            }
            values.put(fieldName, value);
        }
        return values;
    }

    private T newInstanceFromConstructor(Map<String, Object> values, ParameterInfo[] params) {
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            args[i] = values.getOrDefault(params[i].name(), defaultValue(resolveTargetType(params[i].type())));
        }
        return reflector.newInstance(args);
    }

    private ParameterInfo[] constructorParams() {
        if (reflector.getClassInfo() == null || reflector.getClassInfo().params() == null) {
            if (entityType.isRecord()) {
                throw new SqlExecutorException("No constructor metadata available for record type: " + entityType.getName());
            }
            return new ParameterInfo[0];
        }
        if (entityType.isRecord() && reflector.getClassInfo().params().length == 0) {
            throw new SqlExecutorException("No constructor metadata available for record type: " + entityType.getName());
        }
        return reflector.getClassInfo().params();
    }

    private void applyValues(T instance, Map<String, Object> values, Set<String> skipFields) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (skipFields.contains(entry.getKey())) {
                continue;
            }
            reflector.set(instance, entry.getKey(), entry.getValue());
        }
    }

    private Set<String> constructorParamNames(ParameterInfo[] params) {
        Set<String> names = new HashSet<>();
        for (ParameterInfo param : params) {
            names.add(param.name());
        }
        return names;
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

    private Object defaultValue(Class<?> type) {
        if (type == null || !type.isPrimitive()) {
            return null;
        }
        return switch (type.getName()) {
            case "boolean" -> false;
            case "byte" -> (byte) 0;
            case "short" -> (short) 0;
            case "int" -> 0;
            case "long" -> 0L;
            case "float" -> 0F;
            case "double" -> 0D;
            case "char" -> '\0';
            default -> null;
        };
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
