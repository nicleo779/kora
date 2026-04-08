package com.nicleo.kora.core.runtime;

import java.util.Arrays;

public final class SqlRequest {
    private final String sql;
    private final Object[] args;

    public SqlRequest(String sql, Object[] args) {
        this.sql = sql;
        this.args = args == null ? new Object[0] : args.clone();
    }

    public String getSql() {
        return sql;
    }

    public Object[] getArgs() {
        return args.clone();
    }

    public SqlRequest withSql(String sql) {
        return new SqlRequest(sql, args);
    }

    public SqlRequest withArgs(Object[] args) {
        return new SqlRequest(sql, args);
    }

    @Override
    public String toString() {
        return "SqlRequest{" +
                "sql='" + sql + '\'' +
                ", args=" + Arrays.deepToString(args) +
                '}';
    }
}
