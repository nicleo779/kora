package com.nicleo.kora.core.runtime;

import java.util.List;

public interface SqlSession extends AutoCloseable {
    <T> T selectOne(String sql, Object[] args, Class<T> resultType);

    <T> List<T> selectList(String sql, Object[] args, Class<T> resultType);

    int update(String sql, Object[] args);

    int[] executeBatch(String sql, List<Object[]> batchArgs);

    TypeConverter getTypeConverter();

    default <T> void registerTypeConverter(Class<T> targetType, CustomTypeConverter<? extends T> converter) {
        getTypeConverter().register(targetType, converter);
    }

    default void clearTypeConverters() {
        getTypeConverter().clearCustomConverters();
    }

    default <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        return selectOne(sql, args, resultType);
    }

    default <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
        return selectList(sql, args, resultType);
    }

    default int update(String sql, Object[] args, SqlExecutionContext context) {
        return update(sql, args);
    }

    default int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context) {
        return executeBatch(sql, batchArgs);
    }

    default void clearCache() {
    }

    default SqlPagingSupport getSqlPagingSupport() {
        throw new SqlSessionException("SqlPagingSupport is not available for session: " + getClass().getName());
    }

    default DbType getDbType() {
        return DbType.MYSQL;
    }

    default SqlGenerator getSqlGenerator() {
        throw new SqlSessionException("SqlGenerator is not available for session: " + getClass().getName());
    }

    @Override
    default void close() {
    }
    <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper);
}
