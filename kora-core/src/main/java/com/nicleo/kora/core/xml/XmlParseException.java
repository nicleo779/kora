package com.nicleo.kora.core.xml;

public final class XmlParseException extends RuntimeException {
    public XmlParseException(String message) {
        super(message);
    }

    public XmlParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
