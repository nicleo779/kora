package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.dialect.IdentifierPolicy;

public final class StandardIdentifierPolicy implements IdentifierPolicy {
    private final String prefix;
    private final String suffix;

    public StandardIdentifierPolicy(String quote) {
        this(quote, quote);
    }

    public StandardIdentifierPolicy(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    @Override
    public String quote(String identifier) {
        return prefix + identifier + suffix;
    }
}
