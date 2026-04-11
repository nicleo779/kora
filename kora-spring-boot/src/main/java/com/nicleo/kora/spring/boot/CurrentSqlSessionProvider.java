package com.nicleo.kora.spring.boot;

import com.nicleo.kora.core.runtime.SqlSession;

@FunctionalInterface
public interface CurrentSqlSessionProvider {
    SessionHandle currentOrOpen();

    record SessionHandle(SqlSession sqlSession, boolean closeRequired) {
    }
}
