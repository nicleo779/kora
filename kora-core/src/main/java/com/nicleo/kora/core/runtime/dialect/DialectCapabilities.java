package com.nicleo.kora.core.runtime.dialect;

public record DialectCapabilities(
        boolean supportsOffsetFetch,
        boolean supportsUpdateLimit,
        boolean supportsDeleteLimit,
        boolean supportsReturning,
        boolean supportsUpsert,
        boolean supportsWithClause,
        boolean supportsWindowFunctions,
        boolean requiresOrderByForOffsetFetch
) {
}
