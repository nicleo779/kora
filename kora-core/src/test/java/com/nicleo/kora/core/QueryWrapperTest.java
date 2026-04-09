package com.nicleo.kora.core;

import com.nicleo.kora.core.query.Column;
import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.Wrapper;
import com.nicleo.kora.core.runtime.DefaultSqlGenerator;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.SqlRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryWrapperTest {
    private final DefaultSqlGenerator sqlGenerator = new DefaultSqlGenerator();

    @Test
    void predicateBuilderShouldRenderConditionalPredicates() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.<TestUser>of()
                .selectAll()
                .from(users)
                .where(where -> where
                        .eq(true, users.ID, 1L)
                        .like(false, users.NAME, "%skip%")
                        .isNotNull(users.NAME)
                        .in(users.ID, List.of(1L, 2L, 3L))
                        .between(users.AGE, 18, 30))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users WHERE users.id = ? AND users.name IS NOT NULL AND users.id IN (?, ?, ?) AND users.age BETWEEN ? AND ?",
                request.getSql());
        assertArrayEquals(new Object[]{1L, 1L, 2L, 3L, 18, 30}, request.getArgs());
    }

    @Test
    void inAndNotInShouldHandleEmptyCollections() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.<TestUser>of()
                .selectAll()
                .from(users)
                .where(where -> where
                        .in(users.ID, List.of())
                        .notIn(users.AGE, List.of()))
                .toDefinition(), DbType.MYSQL);

        assertEquals("SELECT users.* FROM users WHERE 1 = 0 AND 1 = 1", request.getSql());
        assertArrayEquals(new Object[0], request.getArgs());
    }

    private static final class TestUser {
    }

    private static final class TestUserTable extends EntityTable<TestUser> {
        private static final TestUserTable USERS = new TestUserTable("users", null);

        private final Column<TestUser, Long> ID = column("id", Long.class);
        private final Column<TestUser, String> NAME = column("name", String.class);
        private final Column<TestUser, Integer> AGE = column("age", Integer.class);

        private TestUserTable(String tableName, String alias) {
            super(TestUser.class, tableName, alias);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> Column<TestUser, V> idColumn() {
            return (Column<TestUser, V>) ID;
        }

        @Override
        public String fieldName(String column) {
            return switch (column) {
                case "id" -> "id";
                case "name" -> "name";
                case "age" -> "age";
                default -> column;
            };
        }

        @Override
        public String columnName(String field) {
            return switch (field) {
                case "id" -> "id";
                case "name" -> "name";
                case "age" -> "age";
                default -> field;
            };
        }
    }
}
