package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.xml.SqlCommandType;

import java.util.Objects;

public final class SqlExecutionContext {
    private final SqlExecutor sqlExecutor;
    private final String mapperClassName;
    private final String statementId;
    private final SqlCommandType commandType;
    private final Class<?> resultType;
    private final Paging paging;
    private final SqlRequest countRequest;
    private final boolean interceptorEnabled;

    public SqlExecutionContext(SqlExecutor sqlExecutor, String mapperClassName, String statementId, SqlCommandType commandType, Class<?> resultType, boolean interceptorEnabled) {
        this(sqlExecutor, mapperClassName, statementId, commandType, resultType, null, null, interceptorEnabled);
    }

    public SqlExecutionContext(SqlExecutor sqlExecutor, String mapperClassName, String statementId, SqlCommandType commandType, Class<?> resultType, Paging paging, boolean interceptorEnabled) {
        this(sqlExecutor, mapperClassName, statementId, commandType, resultType, paging, null, interceptorEnabled);
    }

    public SqlExecutionContext(SqlExecutor sqlExecutor, String mapperClassName, String statementId, SqlCommandType commandType, Class<?> resultType, Paging paging, SqlRequest countRequest, boolean interceptorEnabled) {
        this.sqlExecutor = sqlExecutor;
        this.mapperClassName = mapperClassName;
        this.statementId = statementId;
        this.commandType = Objects.requireNonNull(commandType, "commandType");
        this.resultType = resultType;
        this.paging = paging;
        this.countRequest = countRequest;
        this.interceptorEnabled = interceptorEnabled;
    }

    public static SqlExecutionContext select(SqlExecutor sqlExecutor, Class<?> resultType) {
        return new SqlExecutionContext(sqlExecutor, null, null, SqlCommandType.SELECT, resultType, null, true);
    }

    public static SqlExecutionContext update(SqlExecutor sqlExecutor) {
        return new SqlExecutionContext(sqlExecutor, null, null, SqlCommandType.UPDATE, null, null, true);
    }

    public SqlExecutionContext withSqlExecutor(SqlExecutor sqlExecutor) {
        return new SqlExecutionContext(sqlExecutor, mapperClassName, statementId, commandType, resultType, paging, countRequest, interceptorEnabled);
    }

    public SqlExecutionContext withoutInterceptors() {
        return new SqlExecutionContext(sqlExecutor, mapperClassName, statementId, commandType, resultType, paging, countRequest, false);
    }

    public SqlExecutor getSqlExecutor() {
        return sqlExecutor;
    }

    public String getMapperClassName() {
        return mapperClassName;
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

    public SqlRequest getCountRequest() {
        return countRequest;
    }

    public boolean isInterceptorEnabled() {
        return interceptorEnabled;
    }
}
