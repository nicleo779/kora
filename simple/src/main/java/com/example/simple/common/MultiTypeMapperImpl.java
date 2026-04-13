package com.example.simple.common;

import com.nicleo.kora.core.annotation.MapperCapability;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.SqlExecutor;

@MapperCapability(MultiTypeMapper.class)
public class MultiTypeMapperImpl<T, V> extends AbstractMapper<T> implements MultiTypeMapper<T, V> {
    public MultiTypeMapperImpl(SqlExecutor sqlExecutor, Class<T> entityClass) {
        super(sqlExecutor, entityClass);
    }

    @Override
    public String firstTypeName() {
        return entityClass == null ? "null" : entityClass.getName();
    }
}
