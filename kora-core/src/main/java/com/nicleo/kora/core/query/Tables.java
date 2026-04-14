package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.SqlExecutorException;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class Tables {
    private static final Map<Class<?>, EntityTable<?>> REGISTRY = new ConcurrentHashMap<>();

    private Tables() {
    }

    public static <T> void register(Class<T> entityType, EntityTable<? extends T> entityTable) {
        REGISTRY.put(Objects.requireNonNull(entityType, "entityType"), Objects.requireNonNull(entityTable, "entityTable"));
    }

    @SuppressWarnings("unchecked")
    public static <T> EntityTable<T> get(Class<T> entityType) {
        EntityTable<?> table = REGISTRY.get(Objects.requireNonNull(entityType, "entityType"));
        if (table == null) {
            throw new SqlExecutorException("No EntityTable registered for type: " + entityType.getName());
        }
        return (EntityTable<T>) table;
    }

    public static void clear() {
        REGISTRY.clear();
    }
}
