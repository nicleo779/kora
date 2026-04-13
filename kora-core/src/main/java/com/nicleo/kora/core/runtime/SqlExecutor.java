package com.nicleo.kora.core.runtime;

import java.util.List;

public interface SqlExecutor {
    <T> T selectOne(String sql, Object[] args, Class<T> resultType);

    <T> List<T> selectList(String sql, Object[] args, Class<T> resultType);

    int update(String sql, Object[] args);

    Object updateAndReturnGeneratedKey(String sql, Object[] args);

    int[] executeBatch(String sql, List<Object[]> batchArgs);

    TypeConverter getTypeConverter();

    void setTypeConverter(TypeConverter typeConverter);

    IdGenerator getIdGenerator();

    void setIdGenerator(IdGenerator idGenerator);

    <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType);

    <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType);

    int update(String sql, Object[] args, SqlExecutionContext context);

    int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context);

    SqlPagingSupport getSqlPagingSupport();

    DbType getDbType();

    SqlGenerator getSqlGenerator();

    <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper);
}
