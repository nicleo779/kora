package com.nicleo.kora.core.runtime;

public class SqlExecutorException extends RuntimeException {
    public SqlExecutorException(String message) {
        super(message);
    }

    public SqlExecutorException(String message, Throwable cause) {
        super(message, cause);
    }
}
