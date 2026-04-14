package com.nicleo.kora.spring.boot;

import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlExecutor;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Collection;

public class SpringTransactionSqlExecutor extends DefaultSqlExecutor {
    public SpringTransactionSqlExecutor(DataSource dataSource) {
        super(dataSource);
    }

    public void addInterceptor(Collection<SqlInterceptor> interceptors) {
        super.addInterceptor(interceptors);
    }

    @Override
    protected Connection openConnection() {
        return new DelegateConnection(DataSourceUtils.getConnection(getDataSource()), getDataSource());
    }
}
