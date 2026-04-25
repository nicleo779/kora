package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.DialectCapabilities;

public final class StandardOffsetFetchDialect extends AbstractSqlDialect {
    private StandardOffsetFetchDialect(String id, DbType dbType, NativeIdentifierPolicy identifierPolicy) {
        super(
                id,
                dbType,
                identifierPolicy,
                new DialectCapabilities(true, false, false, false, false, true, true, true),
                new OffsetFetchPagingRenderer()
        );
    }

    public static StandardOffsetFetchDialect oracle() {
        return new StandardOffsetFetchDialect("oracle", DbType.ORACLE, NativeIdentifierPolicy.oracle());
    }

    public static StandardOffsetFetchDialect sqlServer() {
        return new StandardOffsetFetchDialect("sqlserver", DbType.SQLSERVER, NativeIdentifierPolicy.sqlServer());
    }
}
