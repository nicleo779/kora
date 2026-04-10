package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;

import java.util.UUID;

public final class UuidIdGenerator implements IdGenerator {
    private final Class<?> targetType;

    public UuidIdGenerator(Class<?> targetType) {
        this.targetType = targetType;
    }

    @Override
    public Object generate(SqlSession sqlSession, EntityTable<?> entityTable, Object entity) {
        UUID uuid = UUID.randomUUID();
        if (targetType == UUID.class) {
            return uuid;
        }
        if (targetType == String.class) {
            return uuid.toString();
        }
        throw new SqlSessionException("UUID id strategy only supports String or UUID fields: "
                + entityTable.entityType().getName() + "." + entityTable.fieldName(entityTable.idColumn().columnName()));
    }
}
