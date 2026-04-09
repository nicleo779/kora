package com.example.simple.common;

import com.example.simple.entity.User;
import com.nicleo.kora.core.annotation.MapperCapability;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.SqlSession;

@MapperCapability(UpdateMapper.class)
public class UpdateMapperImpl<T> extends AbstractMapper<T> implements UpdateMapper<T> {
    public UpdateMapperImpl(SqlSession sqlSession, Class<?> entityClass) {
        super(sqlSession, entityClass);
    }

    @Override
    public int updateNameById(Long id, String name) {
        if (entityClass == User.class) {
            return sqlSession.update("update users set name = ? where id = ?", new Object[]{name, id});
        }
        throw new IllegalArgumentException("Unsupported entity class: " + entityClass.getName());
    }
}
