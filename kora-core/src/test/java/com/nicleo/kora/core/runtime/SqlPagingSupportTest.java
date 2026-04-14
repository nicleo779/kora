package com.nicleo.kora.core.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlPagingSupportTest {
    private final DefaultSqlPagingSupport pagingSupport = new DefaultSqlPagingSupport();

    @Test
    void buildCountSqlShouldRewriteSimpleSelectToDirectCount() {
        String sql = "select id, name from user where age >= ? order by id";

        assertEquals(
                "select count(*) from user where age >= ?",
                pagingSupport.buildCountSql(sql)
        );
    }

    @Test
    void buildCountSqlShouldFallbackToSubqueryWhenGrouped() {
        String sql = "select age, count(*) from user group by age order by age";

        assertEquals(
                "select count(*) from (select age, count(*) from user group by age order by age) kora_page_total",
                pagingSupport.buildCountSql(sql)
        );
    }
}
