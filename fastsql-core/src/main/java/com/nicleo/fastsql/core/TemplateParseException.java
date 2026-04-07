package com.nicleo.fastsql.core;

public final class TemplateParseException extends RuntimeException {
    private final int offset;

    public TemplateParseException(String message, int offset) {
        super(message);
        this.offset = offset;
    }

    public int getOffset() {
        return offset;
    }
}
