package com.nicleo.kora.core.runtime;

public interface SqlInterceptor {
    SqlRequest intercept(SqlExecutionContext context, SqlRequest request);
}
