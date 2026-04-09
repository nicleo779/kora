package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.TypeConverter;
import com.nicleo.kora.core.util.DefaultNameConverter;
import com.nicleo.kora.core.util.NameConverter;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Objects;

public final class GeneratedRowMapper<T> implements RowMapper<T> {
    private final Class<T> entityType;
    private final GeneratedReflector<T> reflector;
    private final NameConverter nameConverter;
    private final TypeConverter typeConverter;

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector) {
        this(entityType, reflector, DefaultNameConverter.INSTANCE, new TypeConverter());
    }

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector, NameConverter nameConverter) {
        this(entityType, reflector, nameConverter, new TypeConverter());
    }

    public GeneratedRowMapper(Class<T> entityType, GeneratedReflector<T> reflector, NameConverter nameConverter, TypeConverter typeConverter) {
        this.entityType = Objects.requireNonNull(entityType, "entityType");
        this.reflector = Objects.requireNonNull(reflector, "reflector");
        this.nameConverter = Objects.requireNonNull(nameConverter, "nameConverter");
        this.typeConverter = Objects.requireNonNull(typeConverter, "typeConverter");
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
        return nameConverter.columnToField(entityType, columnLabel);
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
}
