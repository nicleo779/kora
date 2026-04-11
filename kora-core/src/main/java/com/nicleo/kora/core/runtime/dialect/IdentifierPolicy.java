package com.nicleo.kora.core.runtime.dialect;

public interface IdentifierPolicy {
    String quote(String identifier);

    default String tableReference(String tableName, String alias) {
        return alias == null || alias.isBlank() ? quote(tableName) : quote(tableName) + " " + quote(alias);
    }

    default String columnReference(String qualifier, String columnName) {
        return qualifier == null || qualifier.isBlank()
                ? quote(columnName)
                : quote(qualifier) + "." + quote(columnName);
    }
}
