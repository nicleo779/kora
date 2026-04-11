package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;

public interface SqlPagingSupport {
    <T> Page<T> page(SqlSession sqlSession, SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType);

    default long count(SqlSession sqlSession, SqlExecutionContext context, String sql, Object[] args) {
        throw new SqlSessionException("Count is not supported for paging support: " + getClass().getName());
    }
}
