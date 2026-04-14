package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.SqlRequest;

import java.util.ArrayList;
import java.util.List;

public final class RenderContext {
    private final SqlDialect dialect;
    private final StringBuilder sql = new StringBuilder();
    private final List<Object> args = new ArrayList<>();

    public RenderContext(SqlDialect dialect) {
        this.dialect = dialect;
    }

    public SqlDialect dialect() {
        return dialect;
    }

    public StringBuilder sql() {
        return sql;
    }

    public List<Object> args() {
        return args;
    }

    public SqlRequest toRequest() {
        return new SqlRequest(sql.toString(), args.toArray());
    }
}
