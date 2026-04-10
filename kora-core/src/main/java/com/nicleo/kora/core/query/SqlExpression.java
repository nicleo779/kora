package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

public interface SqlExpression {
    void appendTo(StringBuilder sql, List<Object> args, DbType dbType);

    default SqlExpression as(String alias) {
        return new AliasedExpression(this, alias);
    }

    default Order asc() {
        return new Order(this, true);
    }

    default Order desc() {
        return new Order(this, false);
    }
}
