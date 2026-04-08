package com.nicleo.kora.core.runtime;

import java.util.List;

public interface SqlSession {
    <T> T selectOne(String sql, Object[] args, Class<T> resultType);

    <T> List<T> selectList(String sql, Object[] args, Class<T> resultType);

    int update(String sql, Object[] args);
}
