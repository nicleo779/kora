package com.nicleo.kora.quarkus;

import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlExecutor;

import javax.sql.DataSource;
import java.util.Collection;

public class QuarkusSqlExecutor extends DefaultSqlExecutor {
    public QuarkusSqlExecutor(DataSource dataSource) {
        super(dataSource);
    }

    public void addInterceptor(Collection<SqlInterceptor> interceptors) {
        super.addInterceptor(interceptors);
    }
}
