package com.nicleo.kora.core.xml;

public enum SqlCommandType {
    SELECT,
    INSERT,
    UPDATE,
    DELETE;

    public static SqlCommandType fromElementName(String name) {
        return SqlCommandType.valueOf(name.toUpperCase());
    }
}
