package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

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
            reflector.set(instance, metaData.getColumnLabel(columnIndex), resultSet.getObject(columnIndex));
        }
        return instance;
    }
}
