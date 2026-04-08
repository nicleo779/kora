package com.nicleo.kora.core.runtime;

public class SqlSessionException extends RuntimeException {
    public SqlSessionException(String message) {
        super(message);
    }

    public SqlSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
