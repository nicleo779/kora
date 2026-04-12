package com.example.simple.common;

import com.nicleo.kora.core.annotation.MapperCapability;
import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.SqlExecutor;

@MapperCapability(UpdateMapper.class)
public class UpdateMapperImpl<T> extends AbstractMapper<T> implements UpdateMapper<T> {
    public UpdateMapperImpl(SqlExecutor sqlExecutor, EntityTable<T> entityTable) {
        super(sqlExecutor, entityTable);
    }

    @Override
    public int updateNameById(Long id, String name) {
        String sql = "update " + entityTable.tableName()
                + " set " + entityTable.columnName("name") + " = ? where "
                + entityTable.idColumn().columnName() + " = ?";
        return sqlExecutor.update(sql, new Object[]{name, id});
    }
}
