package com.nicleo.kora.spring.boot;

import com.nicleo.kora.core.mapper.BaseMapperImpl;
import com.nicleo.kora.core.query.*;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionException;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

public final class Sql {
    private static volatile CurrentSqlSessionProvider currentSqlSessionProvider;

    private Sql() {
    }

    public static CurrentSqlSessionProvider bind(CurrentSqlSessionProvider currentSqlSessionProvider) {
        Sql.currentSqlSessionProvider = currentSqlSessionProvider;
        return currentSqlSessionProvider;
    }

    public static void clear() {
        Sql.currentSqlSessionProvider = null;
    }

    public static QueryWrapper query() {
        CurrentSqlSessionProvider provider = currentSqlSessionProvider;
        if (provider == null) {
            throw new SqlSessionException("CurrentSqlSessionProvider is not bound. Did you enable kora-spring-boot auto-configuration?");
        }
        return new QueryWrapper(() -> {
            CurrentSqlSessionProvider.SessionHandle handle = provider.currentOrOpen();
            return new QueryWrapper.SessionHandle(handle.sqlSession(), handle.closeRequired());
        });
    }

    public static <T> QueryWrapper from(EntityTable<T> table) {
        return query().selectAll().from(table);
    }

    public static <T> T select(EntityTable<T> table, Condition... condition) {
        return query().selectAll().from(table).where(condition).one(table.entityType());
    }

    public static <T> List<T> selectList(EntityTable<T> table, Condition... condition) {
        return query().selectAll().from(table).where(condition).list(table.entityType());
    }

    public static <T> int insert(T entity) {
        return withEntityMapper(entity, mapper -> mapper.insert(entity));
    }

    public static <T> int insert(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        T first = entities.iterator().next();
        return withEntityMapper(first, mapper -> mapper.insert(entities));
    }

    public static <T> int updateById(T entity) {
        return withEntityMapper(entity, mapper -> mapper.updateById(entity));
    }

    public static <T> int updateById(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        T first = entities.iterator().next();
        return withEntityMapper(first, mapper -> mapper.updateById(entities));
    }

    public static <T> int deleteById(Class<T> entityType, Serializable id) {
        return withEntityTypeMapper(entityType, mapper -> mapper.deleteById(id));
    }

    public static <T> int deleteByIds(Class<T> entityType, Collection<Serializable> ids) {
        return withEntityTypeMapper(entityType, mapper -> mapper.deleteByIds(ids));
    }

    public static <T> int update(EntityTable<T> table, UpdateWrapper updateWrapper) {
        return withTableMapper(table, mapper -> mapper.update(updateWrapper));
    }

    public static <T> int delete(EntityTable<T> table, WhereWrapper whereWrapper) {
        return withTableMapper(table, mapper -> mapper.delete(whereWrapper));
    }

    private static <T, R> R withEntityMapper(T entity, Function<BaseMapperImpl<T>, R> action) {
        if (entity == null) {
            throw new SqlSessionException("Entity must not be null");
        }
        @SuppressWarnings("unchecked")
        Class<T> entityType = (Class<T>) entity.getClass();
        return withEntityTypeMapper(entityType, action);
    }

    private static <T, R> R withEntityTypeMapper(Class<T> entityType, Function<BaseMapperImpl<T>, R> action) {
        return withTableMapper(Tables.get(entityType), action);
    }

    private static <T, R> R withTableMapper(EntityTable<T> table, Function<BaseMapperImpl<T>, R> action) {
        CurrentSqlSessionProvider provider = currentSqlSessionProvider;
        if (provider == null) {
            throw new SqlSessionException("CurrentSqlSessionProvider is not bound. Did you enable kora-spring-boot auto-configuration?");
        }
        CurrentSqlSessionProvider.SessionHandle handle = provider.currentOrOpen();
        SqlSession sqlSession = handle.sqlSession();
        try {
            BaseMapperImpl<T> mapper = new BaseMapperImpl<>(sqlSession, table);
            return action.apply(mapper);
        } finally {
            if (handle.closeRequired()) {
                sqlSession.close();
            }
        }
    }
}
