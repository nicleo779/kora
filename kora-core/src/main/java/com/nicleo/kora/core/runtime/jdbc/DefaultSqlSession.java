package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.SqlExecutionContext;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultSqlSession implements SqlSession {
    private final DataSource dataSource;
    private final List<SqlInterceptor> interceptors;
    private final Map<CacheKey, List<?>> localCache = new ConcurrentHashMap<>();

    public DefaultSqlSession(DataSource dataSource) {
        this(dataSource, List.of());
    }

    public DefaultSqlSession(DataSource dataSource, SqlInterceptor... interceptors) {
        this(dataSource, Arrays.asList(interceptors));
    }

    public DefaultSqlSession(DataSource dataSource, List<SqlInterceptor> interceptors) {
        this.dataSource = dataSource;
        this.interceptors = List.copyOf(interceptors);
    }

    @Override
    public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
        return selectOne(sql, args, SqlExecutionContext.select(this, resultType), resultType);
    }

    @Override
    public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        List<T> results = selectList(sql, args, context, resultType);
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
        return selectList(sql, args, SqlExecutionContext.select(this, resultType), resultType);
    }

    @Override
    public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        SqlRequest request = applyInterceptors(context, new SqlRequest(sql, args));
        CacheKey cacheKey = new CacheKey(request.getSql(), request.getArgs(), resultType);
        List<T> cached = cachedResults(cacheKey);
        if (cached != null) {
            return copyResults(cached);
        }
        GeneratedReflector<T> reflector = GeneratedReflectors.get(resultType);
        List<T> loaded = executeQuery(request.getSql(), request.getArgs(), new GeneratedRowMapper<>(reflector));
        localCache.put(cacheKey, List.copyOf(loaded));
        return copyResults(loaded);
    }

    @Override
    public int update(String sql, Object[] args) {
        return update(sql, args, SqlExecutionContext.update(this));
    }

    @Override
    public int update(String sql, Object[] args, SqlExecutionContext context) {
        SqlRequest request = applyInterceptors(context, new SqlRequest(sql, args));
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(request.getSql())) {
            bindParameters(statement, request.getArgs());
            int updated = statement.executeUpdate();
            clearCache();
            return updated;
        } catch (SQLException ex) {
            throw new SqlSessionException("Failed to execute update: " + request.getSql(), ex);
        }
    }

    @Override
    public void clearCache() {
        localCache.clear();
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

    private <T> List<T> copyResults(List<T> results) {
        return new ArrayList<>(results);
    }

    private SqlRequest applyInterceptors(SqlExecutionContext context, SqlRequest originalRequest) {
        SqlExecutionContext effectiveContext = context == null ? SqlExecutionContext.update(this) : context.withSqlSession(this);
        if (!effectiveContext.isInterceptorEnabled()) {
            return originalRequest;
        }
        SqlRequest current = originalRequest;
        for (SqlInterceptor interceptor : interceptors) {
            SqlRequest intercepted = interceptor.intercept(effectiveContext.withoutInterceptors(), current);
            if (intercepted == null) {
                throw new SqlSessionException("SqlInterceptor returned null request: " + interceptor.getClass().getName());
            }
            current = intercepted;
        }
        return current;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> cachedResults(CacheKey cacheKey) {
        return (List<T>) localCache.get(cacheKey);
    }

    private void bindParameters(PreparedStatement statement, Object[] args) throws SQLException {
        if (args == null) {
            return;
        }
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
    }

    private static final class CacheKey {
        private final String sql;
        private final Object[] args;
        private final Class<?> resultType;

        private CacheKey(String sql, Object[] args, Class<?> resultType) {
            this.sql = sql;
            this.args = args == null ? new Object[0] : args.clone();
            this.resultType = resultType;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey cacheKey)) {
                return false;
            }
            return Objects.equals(sql, cacheKey.sql)
                    && Arrays.deepEquals(args, cacheKey.args)
                    && Objects.equals(resultType, cacheKey.resultType);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(sql, resultType);
            result = 31 * result + Arrays.deepHashCode(args);
            return result;
        }
    }
}
