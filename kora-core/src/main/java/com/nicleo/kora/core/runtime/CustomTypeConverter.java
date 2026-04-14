package com.nicleo.kora.core.runtime;

import java.sql.ResultSet;

public interface CustomTypeConverter {
    boolean supports(Class<?> targetType);

    <T> T columnToField(ResultSet resultSet, int index, Class<T> targetType);

    Object fieldToColumn(Object value);
}
