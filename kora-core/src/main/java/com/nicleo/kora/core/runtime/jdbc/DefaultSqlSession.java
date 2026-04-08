package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class DefaultSqlSession implements SqlSession {
    private final DataSource dataSource;

    public DefaultSqlSession(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
        List<T> results = selectList(sql, args, resultType);
        if (results.isEmpty()) {
            return null;
        }
        if (results.size() > 1) {
            throw new SqlSessionException("Expected one row but got " + results.size() + " for sql: " + sql);
        }
        return results.get(0);
    }

    @Override
    public <T> List<T> selectList(String sql, Object[] args, Class<T> resultType) {
        GeneratedReflector<T> reflector = GeneratedReflectors.get(resultType);
        return executeQuery(sql, args, new GeneratedRowMapper<>(reflector));
    }

    @Override
    public int update(String sql, Object[] args) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, args);
            return statement.executeUpdate();
        } catch (SQLException ex) {
            throw new SqlSessionException("Failed to execute update: " + sql, ex);
        }
    }

    public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bindParameters(statement, args);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<T> results = new ArrayList<>();
                while (resultSet.next()) {
                    results.add(rowMapper.mapRow(resultSet));
                }
                return results;
            }
        } catch (SQLException ex) {
            throw new SqlSessionException("Failed to execute query: " + sql, ex);
        }
    }

    private void bindParameters(PreparedStatement statement, Object[] args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }
}
