package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.DbType;

import java.util.Collection;

public interface SqlDialectRegistry {
    SqlDialect require(DbType dbType);

    Collection<SqlDialect> all();
}
