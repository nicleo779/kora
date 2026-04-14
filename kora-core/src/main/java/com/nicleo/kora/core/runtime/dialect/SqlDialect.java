package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.runtime.DbType;

public interface SqlDialect {
    String id();

    DbType dbType();

    IdentifierPolicy identifiers();

    DialectCapabilities capabilities();

    PagingRenderer paging();

    QueryRenderer queryRenderer();

    UpdateRenderer updateRenderer();

    DeleteRenderer deleteRenderer();

    InsertRenderer insertRenderer();

    CountQueryRewriter countQueryRewriter();
}
