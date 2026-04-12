package com.nicleo.kora.spring.boot;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

import java.sql.*;

@RequiredArgsConstructor
public class DelegateConnection implements Connection {
    @Delegate
    private final Connection connection;
    @Override
    public void close() {
    }
}
