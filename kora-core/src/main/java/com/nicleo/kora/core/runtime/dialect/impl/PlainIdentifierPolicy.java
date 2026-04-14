package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.dialect.IdentifierPolicy;

public final class PlainIdentifierPolicy implements IdentifierPolicy {
    @Override
    public String quote(String identifier) {
        return identifier;
    }
}
