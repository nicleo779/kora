package com.example.simple.common;

import com.nicleo.kora.core.annotation.MapperCapability;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.SqlExecutor;

@MapperCapability(ViewTypeMapper.class)
public class ViewTypeMapperImpl<T> extends AbstractMapper<T> implements ViewTypeMapper<T> {
    public ViewTypeMapperImpl(SqlExecutor sqlExecutor, Class<T> entityClass) {
        super(sqlExecutor, entityClass);
    }

    @Override
    public String mappedTypeName() {
        return entityClass == null ? "null" : entityClass.getName();
    }
}
