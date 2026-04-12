package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;

import java.util.UUID;

public final class DefaultIdGenerator implements IdGenerator {
    public static final DefaultIdGenerator INSTANCE = new DefaultIdGenerator();

    private DefaultIdGenerator() {
    }

    @Override
    public Object generate(SqlExecutor sqlExecutor, EntityTable<?> entityTable, Object entity) {
        return switch (entityTable.idStrategy()) {
            case NONE -> null;
            case UUID -> generateUuid(entityTable);
            case CUSTOM -> generateCustom(sqlExecutor, entityTable, entity);
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
        throw new SqlExecutorException("UUID id strategy only supports String or UUID fields: "
                + entityTable.entityType().getName() + "." + entityTable.fieldName(entityTable.idColumn().columnName()));
    }

    private Object generateCustom(SqlExecutor sqlExecutor, EntityTable<?> entityTable, Object entity) {
        IdGenerator generator = entityTable.idGenerator();
        if (generator != null) {
            return generator.generate(sqlExecutor, entityTable, entity);
        }
        IdGenerator executorGenerator = sqlExecutor.getIdGenerator();
        if (executorGenerator != null) {
            return executorGenerator.generate(sqlExecutor, entityTable, entity);
        }
        throw new SqlExecutorException("No custom id generator configured for entity: " + entityTable.entityType().getName());
    }
}
