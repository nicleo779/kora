package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.List;

public interface Condition {
    void appendTo(StringBuilder sql, List<Object> args, DbType dbType);
}
