package com.example.simple.common;

import com.nicleo.kora.core.annotation.MapperCapability;
import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.SqlSession;

@MapperCapability(UpdateMapper.class)
public class UpdateMapperImpl<T> extends AbstractMapper<T> implements UpdateMapper<T> {
    public UpdateMapperImpl(SqlSession sqlSession, EntityTable<T> entityTable) {
        super(sqlSession, entityTable);
    }

    @Override
    public int updateNameById(Long id, String name) {
        String sql = "update " + entityTable.tableName()
                + " set " + entityTable.columnName("name") + " = ? where "
                + entityTable.idColumn().columnName() + " = ?";
        return sqlSession.update(sql, new Object[]{name, id});
    }
}
