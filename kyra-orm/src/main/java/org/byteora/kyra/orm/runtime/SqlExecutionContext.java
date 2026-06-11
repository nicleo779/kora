package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.orm.query.Paging;
import org.byteora.kyra.orm.xml.SqlCommandType;

import java.lang.reflect.Type;
import java.util.Objects;

public final class SqlExecutionContext {
    private static final AnnotationMeta[] NO_ANNOTATIONS = new AnnotationMeta[0];

    private final SqlExecutor sqlExecutor;
    private final Class<?> mapperType;
    private final String statementId;
    private final SqlCommandType commandType;
    private final Class<?> resultType;
    private final Type genericResultType;
    private final Paging paging;
    private final SqlRequest countRequest;
    private final AnnotationMeta[] mapperMethodAnnotations;
    private final boolean interceptorEnabled;

    private SqlExecutionContext(Builder builder) {
        this.sqlExecutor = builder.sqlExecutor;
        this.mapperType = builder.mapperType;
        this.statementId = builder.statementId;
        this.commandType = builder.commandType;
        this.resultType = builder.resultType;
        this.genericResultType = builder.genericResultType == null ? builder.resultType : builder.genericResultType;
        this.paging = builder.paging;
        this.countRequest = builder.countRequest;
        // The supplied array originates from generated mapper code and is treated as immutable, so it
        // is referenced directly here; external reads are protected by cloning in the getter.
        this.mapperMethodAnnotations = builder.mapperMethodAnnotations == null || builder.mapperMethodAnnotations.length == 0
                ? NO_ANNOTATIONS
                : builder.mapperMethodAnnotations;
        this.interceptorEnabled = builder.interceptorEnabled;
    }

    public static Builder builder(SqlCommandType commandType) {
        return new Builder(commandType);
    }

    public static SqlExecutionContext select(SqlExecutor sqlExecutor, Class<?> resultType) {
        return builder(SqlCommandType.SELECT).sqlExecutor(sqlExecutor).resultType(resultType).build();
    }

    public static SqlExecutionContext update(SqlExecutor sqlExecutor) {
        return builder(SqlCommandType.UPDATE).sqlExecutor(sqlExecutor).build();
    }

    public SqlExecutionContext withSqlExecutor(SqlExecutor sqlExecutor) {
        return toBuilder().sqlExecutor(sqlExecutor).build();
    }

    public SqlExecutionContext withoutInterceptors() {
        return toBuilder().interceptorEnabled(false).build();
    }

    private Builder toBuilder() {
        return builder(commandType)
                .sqlExecutor(sqlExecutor)
                .mapper(mapperType, statementId)
                .resultType(resultType, genericResultType)
                .paging(paging)
                .countRequest(countRequest)
                .annotations(mapperMethodAnnotations)
                .interceptorEnabled(interceptorEnabled);
    }

    public SqlExecutor getSqlExecutor() {
        return sqlExecutor;
    }

    public Class<?> getMapperType() {
        return mapperType;
    }

    public String getMapperClassName() {
        return mapperType == null ? null : mapperType.getName();
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
        return mapperMethodAnnotations.length == 0 ? NO_ANNOTATIONS : mapperMethodAnnotations.clone();
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

    public static final class Builder {
        private final SqlCommandType commandType;
        private SqlExecutor sqlExecutor;
        private Class<?> mapperType;
        private String statementId;
        private Class<?> resultType;
        private Type genericResultType;
        private Paging paging;
        private SqlRequest countRequest;
        private AnnotationMeta[] mapperMethodAnnotations = NO_ANNOTATIONS;
        private boolean interceptorEnabled = true;

        private Builder(SqlCommandType commandType) {
            this.commandType = Objects.requireNonNull(commandType, "commandType");
        }

        public Builder sqlExecutor(SqlExecutor sqlExecutor) {
            this.sqlExecutor = sqlExecutor;
            return this;
        }

        public Builder mapper(Class<?> mapperType, String statementId) {
            this.mapperType = mapperType;
            this.statementId = statementId;
            return this;
        }

        public Builder resultType(Class<?> resultType) {
            this.resultType = resultType;
            this.genericResultType = resultType;
            return this;
        }

        public Builder resultType(Class<?> resultType, Type genericResultType) {
            this.resultType = resultType;
            this.genericResultType = genericResultType;
            return this;
        }

        public Builder paging(Paging paging) {
            this.paging = paging;
            return this;
        }

        public Builder countRequest(SqlRequest countRequest) {
            this.countRequest = countRequest;
            return this;
        }

        public Builder annotations(AnnotationMeta[] mapperMethodAnnotations) {
            this.mapperMethodAnnotations = mapperMethodAnnotations;
            return this;
        }

        public Builder interceptorEnabled(boolean interceptorEnabled) {
            this.interceptorEnabled = interceptorEnabled;
            return this;
        }

        public SqlExecutionContext build() {
            return new SqlExecutionContext(this);
        }
    }
}
