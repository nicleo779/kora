package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.dialect.IdentifierPolicy;
import com.nicleo.kora.core.runtime.dialect.SqlDialects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeIdentifierPolicyTest {
    @Test
    void mysqlShouldLeaveSimpleIdentifiersUnquoted() {
        IdentifierPolicy policy = SqlDialects.identifiers(DbType.MYSQL);

        assertEquals("users", policy.quote("users"));
        assertEquals("total_count", policy.quote("total_count"));
        assertEquals("users.id", policy.columnReference("users", "id"));
    }

    @Test
    void mysqlShouldQuoteReservedAndSpecialIdentifiers() {
        IdentifierPolicy policy = SqlDialects.identifiers(DbType.MYSQL);

        assertEquals("`order`", policy.quote("order"));
        assertEquals("`key`", policy.quote("key"));
        assertEquals("`user-name`", policy.quote("user-name"));
        assertEquals("users.`user-name`", policy.columnReference("users", "user-name"));
    }

    @Test
    void postgreSqlShouldQuoteMixedCaseIdentifiers() {
        IdentifierPolicy policy = SqlDialects.identifiers(DbType.POSTGRESQL);

        assertEquals("users", policy.quote("users"));
        assertEquals("\"CamelCase\"", policy.quote("CamelCase"));
        assertEquals("\"user\"", policy.quote("user"));
    }

    @Test
    void oracleShouldQuoteDialectSpecificReservedWords() {
        IdentifierPolicy policy = SqlDialects.identifiers(DbType.ORACLE);

        assertEquals("\"level\"", policy.quote("level"));
    }

    @Test
    void sqlServerShouldEscapeClosingBracket() {
        IdentifierPolicy policy = SqlDialects.identifiers(DbType.SQLSERVER);

        assertEquals("[identity]", policy.quote("identity"));
        assertEquals("[bad]]name]", policy.quote("bad]name"));
    }
}
