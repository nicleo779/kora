package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.SqlDialects;

import java.util.List;

final class AliasReferenceExpression implements SqlExpression {
    private final String alias;

    AliasReferenceExpression(String alias) {
        this.alias = alias;
    }

    @Override
    public void appendTo(StringBuilder sql, List<Object> args, DbType dbType) {
        sql.append(SqlDialects.identifiers(dbType).quote(alias));
    }
}
