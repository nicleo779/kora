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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryWrapperTest {
    private final DefaultSqlGenerator sqlGenerator = new DefaultSqlGenerator();

    @Test
    void predicateBuilderShouldRenderConditionalPredicates() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
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

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .where(where -> where
                        .in(users.ID, List.of())
                        .notIn(users.AGE, List.of()))
                .toDefinition(), DbType.MYSQL);

        assertEquals("SELECT users.* FROM users WHERE 1 = 0 AND 1 = 1", request.getSql());
        assertArrayEquals(new Object[0], request.getArgs());
    }

    @Test
    void selectShouldSupportAggregateFunctions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(
                        Functions.count().as("total"),
                        Functions.avg(users.AGE).as("avg_age"),
                        Functions.max(users.AGE).as("max_age"),
                        Functions.min(users.AGE).as("min_age"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total, AVG(users.age) AS avg_age, MAX(users.age) AS max_age, MIN(users.age) AS min_age FROM users",
                request.getSql());
        assertArrayEquals(new Object[0], request.getArgs());
    }

    @Test
    void ifFunctionShouldRenderPerDbType() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(users.age >= ?, ?, ?) AS age_group FROM users",
                mysqlRequest.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, mysqlRequest.getArgs());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group"))
                .from(users)
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT CASE WHEN users.age >= ? THEN ? ELSE ? END AS age_group FROM users",
                postgresqlRequest.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, postgresqlRequest.getArgs());
    }

    @Test
    void conditionsShouldPropagateDbTypeToNestedExpressions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(Functions.ifElse(
                        Conditions.eq(
                                Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor"),
                                "adult"),
                        1,
                        0).as("adult_flag"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(IF(users.age >= ?, ?, ?) = ?, ?, ?) AS adult_flag FROM users",
                request.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor", "adult", 1, 0}, request.getArgs());
    }

    @Test
    void groupByAndHavingShouldSupportExpressions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(
                        Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group"),
                        Functions.count().as("total"))
                .from(users)
                .groupBy(Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor"))
                .having(having -> having.ge(Functions.count(), 2))
                .orderBy(order -> order.desc(Functions.count()))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(users.age >= ?, ?, ?) AS age_group, COUNT(*) AS total FROM users GROUP BY IF(users.age >= ?, ?, ?) HAVING COUNT(*) >= ? ORDER BY COUNT(*) DESC",
                request.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor", 18, "adult", "minor", 2}, request.getArgs());
    }

    @Test
    void predicateBuilderShouldSupportOrAndNestedGroups() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .where(where -> where
                        .eq(users.ID, 1L)
                        .or(or -> or
                                .eq(users.NAME, "Bob")
                                .and(and -> and
                                        .ge(users.AGE, 18)
                                        .lt(users.AGE, 30))))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users WHERE users.id = ? OR (users.name = ? AND (users.age >= ? AND users.age < ?))",
                request.getSql());
        assertArrayEquals(new Object[]{1L, "Bob", 18, 30}, request.getArgs());
    }

    @Test
    void joinOnShouldSupportPredicateBuilder() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable alias = new TestUserTable("users", "u2");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .leftJoin(alias)
                .on(on -> on
                        .condition(Conditions.eq(users.ID, alias.ID))
                        .or(or -> or
                                .eq(alias.NAME, "Bob")
                                .eq(alias.AGE, 30)))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users LEFT JOIN users u2 ON users.id = u2.id OR (u2.name = ? AND u2.age = ?)",
                request.getSql());
        assertArrayEquals(new Object[]{"Bob", 30}, request.getArgs());
    }

    @Test
    void predicateBuilderShouldSupportNotGroups() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .where(where -> where
                        .eq(users.ID, 1L)
                        .and(and -> and.not(not -> not
                                .eq(users.NAME, "Bob")
                                .or(or -> or.lt(users.AGE, 18)))))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users WHERE users.id = ? AND (NOT (users.name = ? OR (users.age < ?)))",
                request.getSql());
        assertArrayEquals(new Object[]{1L, "Bob", 18}, request.getArgs());
    }

    @Test
    void queryWrapperShouldSupportInnerAndRightJoin() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable innerAlias = new TestUserTable("users", "u2");
        TestUserTable rightAlias = new TestUserTable("users", "u3");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .innerJoin(innerAlias)
                .on(Conditions.eq(users.ID, innerAlias.ID))
                .rightJoin(rightAlias)
                .on(on -> on.condition(Conditions.eq(users.ID, rightAlias.ID)))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users INNER JOIN users u2 ON users.id = u2.id RIGHT JOIN users u3 ON users.id = u3.id",
                request.getSql());
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
