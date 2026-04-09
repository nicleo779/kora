package com.nicleo.kora.spring.boot;

import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlSession;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collection;

public class SpringTransactionSqlSession extends DefaultSqlSession {
    public SpringTransactionSqlSession(DataSource dataSource) {
        super(dataSource);
    }

    public SpringTransactionSqlSession(DataSource dataSource, SqlInterceptor... interceptors) {
        super(dataSource, interceptors);
    }

    public void addInterceptor(Collection<SqlInterceptor> interceptors) {
        super.addInterceptor(interceptors);
    }

    @Override
    protected synchronized Connection currentConnection() throws SQLException {
        if (closed) {
            throw new com.nicleo.kora.core.runtime.SqlSessionException("SqlSession is already closed");
        }
        if (connection == null || connection.isClosed()) {
            connection = DataSourceUtils.getConnection(getDataSource());
        }
        return connection;
    }

    @Override
    protected void releaseConnection(Connection connection) {
        DataSourceUtils.releaseConnection(connection, getDataSource());
    }
}
