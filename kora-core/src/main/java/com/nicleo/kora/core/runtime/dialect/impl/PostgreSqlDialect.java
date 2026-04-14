package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.DialectCapabilities;

public final class PostgreSqlDialect extends AbstractSqlDialect {
    public PostgreSqlDialect() {
        super(
                "postgresql",
                DbType.POSTGRESQL,
                new StandardIdentifierPolicy("\""),
                new DialectCapabilities(false, false, false, true, true, true, true, false),
                new LimitOffsetPagingRenderer()
        );
    }
}
