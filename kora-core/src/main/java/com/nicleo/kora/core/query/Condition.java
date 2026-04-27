package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.RenderContext;

import java.util.List;

public interface Condition {
    void appendTo(StringBuilder sql, List<Object> args, DbType dbType);

    void appendTo(RenderContext context);
}
