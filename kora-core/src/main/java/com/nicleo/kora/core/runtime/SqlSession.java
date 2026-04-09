package com.nicleo.kora.core.runtime;

import java.util.List;

public interface SqlSession {
    <T> T selectOne(String sql, Object[] args, Class<T> resultType);

    <T> List<T> selectList(String sql, Object[] args, Class<T> resultType);

    int update(String sql, Object[] args);

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

    default void clearCache() {
    }
}
