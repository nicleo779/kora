package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.EntityTable;

import java.util.List;

public record InsertModel(
        EntityTable<?> table,
        List<String> columns,
        List<Object> args
) {
}
