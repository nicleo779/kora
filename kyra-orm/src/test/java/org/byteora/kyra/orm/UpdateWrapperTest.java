package org.byteora.kyra.orm;

import org.byteora.kyra.orm.query.Column;
import org.byteora.kyra.orm.query.Conditions;
import org.byteora.kyra.orm.query.EntityTable;
import org.byteora.kyra.orm.query.Functions;
import org.byteora.kyra.orm.query.Wrapper;
import org.byteora.kyra.orm.runtime.DefaultSqlGenerator;
import org.byteora.kyra.orm.runtime.DbType;
import org.byteora.kyra.orm.runtime.SqlRequest;
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
                "update users set name = IF(age >= ?, ?, ?) WHERE id = ?",
                request.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor", 1L}, request.args());
    }

    @Test
    void updateSetShouldSupportNullLiteralValues() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderUpdate(users, Wrapper.update()
                .setNull(users.NAME)
                .where(where -> where.eq(users.ID, 1L))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "update users set name = ? WHERE id = ?",
                request.sql());
        assertArrayEquals(new Object[]{null, 1L}, request.args());
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
        public Column<TestUser, ?> idColumn() {
            return ID;
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
