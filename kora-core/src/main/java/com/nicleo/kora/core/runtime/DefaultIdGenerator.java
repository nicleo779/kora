package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.annotation.IdStrategy;
import com.nicleo.kora.core.query.EntityTable;

import java.util.UUID;

public final class DefaultIdGenerator implements IdGenerator {
    public static final DefaultIdGenerator INSTANCE = new DefaultIdGenerator();

    private DefaultIdGenerator() {
    }

    @Override
    public Object generate(SqlSession sqlSession, EntityTable<?> entityTable, Object entity) {
        return switch (entityTable.idStrategy()) {
            case NONE -> null;
            case UUID -> generateUuid(entityTable);
            case CUSTOM -> generateCustom(sqlSession, entityTable, entity);
        };
    }

    private Object generateUuid(EntityTable<?> entityTable) {
        UUID uuid = UUID.randomUUID();
        Class<?> javaType = entityTable.idColumn().javaType();
        if (javaType == UUID.class) {
            return uuid;
        }
        if (javaType == String.class) {
            return uuid.toString();
        }
        throw new SqlSessionException("UUID id strategy only supports String or UUID fields: "
                + entityTable.entityType().getName() + "." + entityTable.fieldName(entityTable.idColumn().columnName()));
    }

    private Object generateCustom(SqlSession sqlSession, EntityTable<?> entityTable, Object entity) {
        IdGenerator generator = entityTable.idGenerator();
        if (generator != null) {
            return generator.generate(sqlSession, entityTable, entity);
        }
        IdGenerator sessionGenerator = sqlSession.getIdGenerator();
        if (sessionGenerator != null) {
            return sessionGenerator.generate(sqlSession, entityTable, entity);
        }
        throw new SqlSessionException("No custom id generator configured for entity: " + entityTable.entityType().getName());
    }
}
