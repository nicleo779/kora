package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.TypeConverter;
import com.nicleo.kora.core.util.NameUtils;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public final class GeneratedRowMapper<T> implements RowMapper<T> {
    private final GeneratedReflector<T> reflector;

    public GeneratedRowMapper(GeneratedReflector<T> reflector) {
        this.reflector = reflector;
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
            if (fieldInfo != null) {
                Class<?> targetType = resolveTargetType(fieldInfo.type());
                if (targetType != null) {
                    value = TypeConverter.cast(value, targetType);
                }
            }
            reflector.set(instance, fieldName, value);
        }
        return instance;
    }

    private String resolveFieldName(String columnLabel) {
        if (reflector.getField(columnLabel) != null) {
            return columnLabel;
        }
        return NameUtils.snakeToCamel(columnLabel);
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
