package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.IdentifierPolicy;
import com.nicleo.kora.core.runtime.dialect.DialectCapabilities;

public final class StandardLimitDialect extends AbstractSqlDialect {
    private StandardLimitDialect(String id, DbType dbType, IdentifierPolicy identifierPolicy) {
        super(
                id,
                dbType,
                identifierPolicy,
                new DialectCapabilities(false, true, true, false, false, true, true, false),
                new LimitOffsetPagingRenderer()
        );
    }

    public static StandardLimitDialect mysql() {
        return new StandardLimitDialect("mysql", DbType.MYSQL, NativeIdentifierPolicy.mysql());
    }

    public static StandardLimitDialect mariaDb() {
        return new StandardLimitDialect("mariadb", DbType.MARIADB, NativeIdentifierPolicy.mariaDb());
    }

    public static StandardLimitDialect sqlite() {
        return new StandardLimitDialect("sqlite", DbType.SQLITE, NativeIdentifierPolicy.sqlite());
    }

    public static StandardLimitDialect h2() {
        return new StandardLimitDialect("h2", DbType.H2, NativeIdentifierPolicy.h2());
    }
}
