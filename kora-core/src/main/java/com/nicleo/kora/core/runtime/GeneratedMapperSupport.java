package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.dynamic.DynamicSqlArgumentResolver;
import com.nicleo.kora.core.dynamic.DynamicSqlNode;
import com.nicleo.kora.core.dynamic.DynamicSqlRenderer;
import com.nicleo.kora.core.dynamic.MapperParameters;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.xml.SqlCommandType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public final class GeneratedMapperSupport {
    private GeneratedMapperSupport() {
    }

    public static <T> T selectOne(SqlExecutor sqlExecutor,
                                  String mapperClassName,
                                  String statementId,
                                  AnnotationMeta[] mapperMethodAnnotations,
                                  DynamicSqlNode sqlNode,
                                  String[] parameterNames,
                                  Object[] parameterValues,
                                  Class<T> resultType) {
        return selectOne(sqlExecutor, mapperClassName, statementId, mapperMethodAnnotations, sqlNode, parameterNames, parameterValues, (Type) resultType);
    }

    public static <T> T selectOne(SqlExecutor sqlExecutor,
                                  String mapperClassName,
                                  String statementId,
                                  AnnotationMeta[] mapperMethodAnnotations,
                                  DynamicSqlNode sqlNode,
                                  String[] parameterNames,
                                  Object[] parameterValues,
                                  Type resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        Class<T> resultClass = rawClass(resultType);
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                mapperClassName,
                statementId,
                SqlCommandType.SELECT,
                resultClass,
                resultType,
                null,
                null,
                mapperMethodAnnotations,
                true
        );
        return sqlExecutor.selectOne(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context, resultClass);
    }

    public static <T> List<T> selectList(SqlExecutor sqlExecutor,
                                         String mapperClassName,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Class<T> resultType) {
        return selectList(sqlExecutor, mapperClassName, statementId, mapperMethodAnnotations, sqlNode, parameterNames, parameterValues, (Type) resultType);
    }

    public static <T> List<T> selectList(SqlExecutor sqlExecutor,
                                         String mapperClassName,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Type resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        Class<T> resultClass = rawClass(resultType);
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                mapperClassName,
                statementId,
                SqlCommandType.SELECT,
                resultClass,
                resultType,
                null,
                null,
                mapperMethodAnnotations,
                true
        );
        return sqlExecutor.selectList(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context, resultClass);
    }

    public static <T> Page<T> selectPage(SqlExecutor sqlExecutor,
                                         String mapperClassName,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Paging paging,
                                         Class<T> resultType) {
        return selectPage(sqlExecutor, mapperClassName, statementId, mapperMethodAnnotations, sqlNode, parameterNames, parameterValues, paging, (Type) resultType);
    }

    public static <T> Page<T> selectPage(SqlExecutor sqlExecutor,
                                         String mapperClassName,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Paging paging,
                                         Type resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        Class<T> resultClass = rawClass(resultType);
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                mapperClassName,
                statementId,
                SqlCommandType.SELECT,
                resultClass,
                resultType,
                paging,
                null,
                mapperMethodAnnotations,
                true
        );
        return sqlExecutor.getSqlPagingSupport().page(
                sqlExecutor,
                context,
                boundSql.getSql(),
                DynamicSqlArgumentResolver.resolve(boundSql),
                paging,
                resultClass
        );
    }

    public static int update(SqlExecutor sqlExecutor,
                             String mapperClassName,
                             String statementId,
                             AnnotationMeta[] mapperMethodAnnotations,
                             SqlCommandType commandType,
                             DynamicSqlNode sqlNode,
                             String[] parameterNames,
                             Object[] parameterValues) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                mapperClassName,
                statementId,
                commandType,
                null,
                null,
                null,
                mapperMethodAnnotations,
                true
        );
        return sqlExecutor.update(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context);
    }

    @SuppressWarnings("unchecked")
    private static <T> Class<T> rawClass(Type type) {
        if (type instanceof Class<?> clazz) {
            return (Class<T>) clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            return (Class<T>) raw;
        }
        throw new SqlExecutorException("Unsupported mapper result type: " + type);
    }
}
