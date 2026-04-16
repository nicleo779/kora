package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.dynamic.DynamicSqlArgumentResolver;
import com.nicleo.kora.core.dynamic.DynamicSqlNode;
import com.nicleo.kora.core.dynamic.DynamicSqlRenderer;
import com.nicleo.kora.core.dynamic.MapperParameters;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.xml.SqlCommandType;

import java.util.List;
import java.util.Map;

public final class GeneratedMapperSupport {
    private GeneratedMapperSupport() {
    }

    public static <T> T selectOne(SqlExecutor sqlExecutor,
                                  String mapperClassName,
                                  String statementId,
                                  DynamicSqlNode sqlNode,
                                  String[] parameterNames,
                                  Object[] parameterValues,
                                  Class<T> resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                mapperClassName,
                statementId,
                SqlCommandType.SELECT,
                resultType,
                null,
                true
        );
        return sqlExecutor.selectOne(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context, resultType);
    }

    public static <T> List<T> selectList(SqlExecutor sqlExecutor,
                                         String mapperClassName,
                                         String statementId,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Class<T> resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                mapperClassName,
                statementId,
                SqlCommandType.SELECT,
                resultType,
                null,
                true
        );
        return sqlExecutor.selectList(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context, resultType);
    }

    public static <T> Page<T> selectPage(SqlExecutor sqlExecutor,
                                         String mapperClassName,
                                         String statementId,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Paging paging,
                                         Class<T> resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                mapperClassName,
                statementId,
                SqlCommandType.SELECT,
                resultType,
                paging,
                true
        );
        return sqlExecutor.getSqlPagingSupport().page(
                sqlExecutor,
                context,
                boundSql.getSql(),
                DynamicSqlArgumentResolver.resolve(boundSql),
                paging,
                resultType
        );
    }

    public static int update(SqlExecutor sqlExecutor,
                             String mapperClassName,
                             String statementId,
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
                true
        );
        return sqlExecutor.update(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context);
    }
}
