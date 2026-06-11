package org.byteora.kyra.orm.runtime;

import java.util.Arrays;

public record SqlRequest(String sql, Object[] args) {
    public SqlRequest(String sql, Object[] args) {
        this.sql = sql;
        this.args = args == null ? new Object[0] : args.clone();
    }

    @Override
    public Object[] args() {
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
