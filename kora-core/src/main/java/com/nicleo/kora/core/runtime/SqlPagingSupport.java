package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;

public interface SqlPagingSupport {
    <T> Page<T> page(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType);

    long count(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args);
}
