package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.xml.SqlCommandType;

import java.lang.reflect.Type;
import java.util.Objects;

public final class SqlExecutionContext {
    private static final AnnotationMeta[] NO_ANNOTATIONS = new AnnotationMeta[0];

    private final SqlExecutor sqlExecutor;
    private final String mapperClassName;
    private final String statementId;
    private final SqlCommandType commandType;
    private final Class<?> resultType;
    private final Type genericResultType;
    private final Paging paging;
    private final SqlRequest countRequest;
    private final AnnotationMeta[] mapperMethodAnnotations;
    private final boolean interceptorEnabled;

    public SqlExecutionContext(SqlExecutor sqlExecutor, String mapperClassName, String statementId, SqlCommandType commandType, Class<?> resultType, boolean interceptorEnabled) {
        this(sqlExecutor, mapperClassName, statementId, commandType, resultType, resultType, null, null, NO_ANNOTATIONS, interceptorEnabled);
    }

    public SqlExecutionContext(SqlExecutor sqlExecutor, String mapperClassName, String statementId, SqlCommandType commandType, Class<?> resultType, Paging paging, boolean interceptorEnabled) {
        this(sqlExecutor, mapperClassName, statementId, commandType, resultType, resultType, paging, null, NO_ANNOTATIONS, interceptorEnabled);
    }

    public SqlExecutionContext(SqlExecutor sqlExecutor, String mapperClassName, String statementId, SqlCommandType commandType, Class<?> resultType, Paging paging, SqlRequest countRequest, boolean interceptorEnabled) {
        this(sqlExecutor, mapperClassName, statementId, commandType, resultType, resultType, paging, countRequest, NO_ANNOTATIONS, interceptorEnabled);
    }

    public SqlExecutionContext(SqlExecutor sqlExecutor,
                               String mapperClassName,
                               String statementId,
                               SqlCommandType commandType,
                               Class<?> resultType,
                               Paging paging,
                               SqlRequest countRequest,
                               AnnotationMeta[] mapperMethodAnnotations,
                               boolean interceptorEnabled) {
        this(sqlExecutor, mapperClassName, statementId, commandType, resultType, resultType, paging, countRequest, mapperMethodAnnotations, interceptorEnabled);
    }

    public SqlExecutionContext(SqlExecutor sqlExecutor,
                               String mapperClassName,
                               String statementId,
                               SqlCommandType commandType,
                               Class<?> resultType,
                               Type genericResultType,
                               Paging paging,
                               SqlRequest countRequest,
                               AnnotationMeta[] mapperMethodAnnotations,
                               boolean interceptorEnabled) {
        this.sqlExecutor = sqlExecutor;
        this.mapperClassName = mapperClassName;
        this.statementId = statementId;
        this.commandType = Objects.requireNonNull(commandType, "commandType");
        this.resultType = resultType;
        this.genericResultType = genericResultType == null ? resultType : genericResultType;
        this.paging = paging;
        this.countRequest = countRequest;
        this.mapperMethodAnnotations = mapperMethodAnnotations == null || mapperMethodAnnotations.length == 0
                ? NO_ANNOTATIONS
                : mapperMethodAnnotations.clone();
        this.interceptorEnabled = interceptorEnabled;
    }

    public static SqlExecutionContext select(SqlExecutor sqlExecutor, Class<?> resultType) {
        return new SqlExecutionContext(sqlExecutor, null, null, SqlCommandType.SELECT, resultType, null, null, NO_ANNOTATIONS, true);
    }

    public static SqlExecutionContext update(SqlExecutor sqlExecutor) {
        return new SqlExecutionContext(sqlExecutor, null, null, SqlCommandType.UPDATE, null, null, null, NO_ANNOTATIONS, true);
    }

    public SqlExecutionContext withSqlExecutor(SqlExecutor sqlExecutor) {
        return new SqlExecutionContext(sqlExecutor, mapperClassName, statementId, commandType, resultType, genericResultType, paging, countRequest, mapperMethodAnnotations, interceptorEnabled);
    }

    public SqlExecutionContext withoutInterceptors() {
        return new SqlExecutionContext(sqlExecutor, mapperClassName, statementId, commandType, resultType, genericResultType, paging, countRequest, mapperMethodAnnotations, false);
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

    public Type getGenericResultType() {
        return genericResultType;
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

    public AnnotationMeta[] getMapperMethodAnnotations() {
        return mapperMethodAnnotations.clone();
    }

    public AnnotationMeta getMapperMethodAnnotation(String annotationType) {
        if (annotationType == null || annotationType.isBlank()) {
            return null;
        }
        for (AnnotationMeta annotationMeta : mapperMethodAnnotations) {
            if (annotationType.equals(annotationMeta.type())) {
                return annotationMeta;
            }
        }
        return null;
    }

    public AnnotationMeta getMapperMethodAnnotation(Class<?> annotationType) {
        Objects.requireNonNull(annotationType, "annotationType");
        return getMapperMethodAnnotation(annotationType.getName());
    }
}
