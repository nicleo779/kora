package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;

public interface IdGenerator {
    Object generate(SqlSession sqlSession, EntityTable<?> entityTable, Object entity);
}
