package com.nicleo.kora.spring.boot;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.springframework.jdbc.datasource.DataSourceUtils;

import javax.sql.DataSource;
import java.sql.*;

@RequiredArgsConstructor
public class DelegateConnection implements Connection {
    @Delegate
    private final Connection connection;
    private final DataSource dataSource;

    @Override
    public void close() {
        DataSourceUtils.releaseConnection(connection, dataSource);
    }
}
