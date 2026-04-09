package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.xml.SqlCommandType;

import java.util.Objects;

public final class SqlExecutionContext {
    private final SqlSession sqlSession;
    private final String mapperClassName;
    private final String mapperMethodName;
    private final String statementId;
    private final SqlCommandType commandType;
    private final Class<?> resultType;
    private final Paging paging;
    private final boolean interceptorEnabled;

    public SqlExecutionContext(SqlSession sqlSession, String mapperClassName, String mapperMethodName, String statementId, SqlCommandType commandType, Class<?> resultType, boolean interceptorEnabled) {
        this(sqlSession, mapperClassName, mapperMethodName, statementId, commandType, resultType, null, interceptorEnabled);
    }

    public SqlExecutionContext(SqlSession sqlSession, String mapperClassName, String mapperMethodName, String statementId, SqlCommandType commandType, Class<?> resultType, Paging paging, boolean interceptorEnabled) {
        this.sqlSession = sqlSession;
        this.mapperClassName = mapperClassName;
        this.mapperMethodName = mapperMethodName;
        this.statementId = statementId;
        this.commandType = Objects.requireNonNull(commandType, "commandType");
        this.resultType = resultType;
        this.paging = paging;
        this.interceptorEnabled = interceptorEnabled;
    }

    public static SqlExecutionContext select(SqlSession sqlSession, Class<?> resultType) {
        return new SqlExecutionContext(sqlSession, null, null, null, SqlCommandType.SELECT, resultType, null, true);
    }

    public static SqlExecutionContext update(SqlSession sqlSession) {
        return new SqlExecutionContext(sqlSession, null, null, null, SqlCommandType.UPDATE, null, null, true);
    }

    public SqlExecutionContext withSqlSession(SqlSession sqlSession) {
        return new SqlExecutionContext(sqlSession, mapperClassName, mapperMethodName, statementId, commandType, resultType, paging, interceptorEnabled);
    }

    public SqlExecutionContext withoutInterceptors() {
        return new SqlExecutionContext(sqlSession, mapperClassName, mapperMethodName, statementId, commandType, resultType, paging, false);
    }

    public SqlSession getSqlSession() {
        return sqlSession;
    }

    public String getMapperClassName() {
        return mapperClassName;
    }

    public String getMapperMethodName() {
        return mapperMethodName;
    }

    public String getStatementId() {
        return statementId;
    }

    public SqlCommandType getCommandType() {
        return commandType;
    }

    public Class<?> getResultType() {
        return resultType;
    }

    public Paging getPaging() {
        return paging;
    }

    public boolean isInterceptorEnabled() {
        return interceptorEnabled;
    }
}
