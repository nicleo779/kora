package com.nicleo.kora.core.query;

public abstract class EntityTable<T> {
    private final Class<T> entityType;
    private final String tableName;
    private final String alias;

    protected EntityTable(Class<T> entityType, String tableName) {
        this(entityType, tableName, null);
    }

    protected EntityTable(Class<T> entityType, String tableName, String alias) {
        this.entityType = entityType;
        this.tableName = tableName;
        this.alias = alias;
    }

    public final Class<T> entityType() {
        return entityType;
    }

    public final String tableName() {
        return tableName;
    }

    public final String alias() {
        return alias;
    }

    public final String tableReference() {
        return alias == null || alias.isBlank() ? tableName : tableName + " " + alias;
    }

    public final String qualifier() {
        return alias == null || alias.isBlank() ? tableName : alias;
    }

    protected final <V> Column<T, V> column(String columnName, Class<V> javaType) {
        return new Column<>(this, columnName, javaType);
    }

    public abstract <V> Column<T, V> idColumn();
    public abstract String fieldName(String column);
    public abstract String columnName(String field);
}
