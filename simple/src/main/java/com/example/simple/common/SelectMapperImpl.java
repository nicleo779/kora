package com.example.simple.common;

import com.nicleo.kora.core.annotation.MapperCapability;
import com.nicleo.kora.core.query.QueryWrapper;
import com.nicleo.kora.core.query.SelectMapper;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionException;

import java.util.List;

@MapperCapability(SelectMapper.class)
public class SelectMapperImpl<T> extends AbstractMapper<T> implements SelectMapper<T> {
    public SelectMapperImpl(SqlSession sqlSession, Class<?> entityClass) {
        super(sqlSession, entityClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<T> selectList(QueryWrapper<T> query) {
        validateEntityType(query);
        SqlRequest request = query.toSqlRequest();
        return sqlSession.selectList(request.getSql(), request.getArgs(), (Class<T>) entityClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public T selectOne(QueryWrapper<T> query) {
        validateEntityType(query);
        SqlRequest request = query.toSqlRequest();
        return sqlSession.selectOne(request.getSql(), request.getArgs(), (Class<T>) entityClass);
    }

    private void validateEntityType(QueryWrapper<T> query) {
        if (query.entityType() != entityClass) {
            throw new SqlSessionException("QueryWrapper entity type does not match mapper entity type: "
                    + query.entityType().getName() + " vs " + entityClass.getName());
        }
    }
}
