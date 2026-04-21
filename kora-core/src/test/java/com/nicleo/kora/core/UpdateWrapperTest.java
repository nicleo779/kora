package com.nicleo.kora.core;

import com.nicleo.kora.core.query.Column;
import com.nicleo.kora.core.query.Conditions;
import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.Functions;
import com.nicleo.kora.core.query.Wrapper;
import com.nicleo.kora.core.runtime.DefaultSqlGenerator;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.SqlRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UpdateWrapperTest {
    private final DefaultSqlGenerator sqlGenerator = new DefaultSqlGenerator();

    @Test
    void updateSetShouldSupportExpressions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderUpdate(users, Wrapper.update()
                .set(users.NAME, Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor"))
                .where(where -> where.eq(users.ID, 1L))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "update `users` set `name` = IF(`users`.`age` >= ?, ?, ?) WHERE `users`.`id` = ?",
                request.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor", 1L}, request.getArgs());
    }

    @Test
    void updateSetShouldSupportNullLiteralValues() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderUpdate(users, Wrapper.update()
                .setNull(users.NAME)
                .where(where -> where.eq(users.ID, 1L))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "update `users` set `name` = ? WHERE `users`.`id` = ?",
                request.getSql());
        assertArrayEquals(new Object[]{null, 1L}, request.getArgs());
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
