package com.nicleo.kora.core.runtime.dialect;

public record PageClause(
        Integer offset,
        Integer limit,
        boolean forDataMutation
) {
}
