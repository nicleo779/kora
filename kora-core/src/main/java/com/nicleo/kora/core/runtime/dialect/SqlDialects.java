package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.DbType;

public final class SqlDialects {
    private static final SqlDialectRegistry REGISTRY = new DefaultSqlDialectRegistry();

    private SqlDialects() {
    }

    public static SqlDialect dialect(DbType dbType) {
        return REGISTRY.require(dbType);
    }

    public static IdentifierPolicy identifiers(DbType dbType) {
        return dialect(dbType).identifiers();
    }
}
