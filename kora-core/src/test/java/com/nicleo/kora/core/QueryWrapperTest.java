package com.nicleo.kora.core;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nicleo.kora.core.runtime.*;
import org.junit.jupiter.api.Test;

import com.nicleo.kora.core.query.Column;
import com.nicleo.kora.core.query.Conditions;
import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.Functions;
import com.nicleo.kora.core.query.NamedSqlExpression;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.query.QueryWrapper;
import com.nicleo.kora.core.query.Wrapper;

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
                "SELECT `users`.* FROM `users` WHERE `users`.`id` = ? AND `users`.`name` IS NOT NULL AND `users`.`id` IN (?, ?, ?) AND `users`.`age` BETWEEN ? AND ?",
                request.getSql());
        assertArrayEquals(new Object[]{1L, 1L, 2L, 3L, 18, 30}, request.getArgs());
    }

    @Test
    void whereShouldSupportDirectConditionShortcut() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .where(users.ID.eq(1L))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT `users`.* FROM `users` WHERE `users`.`id` = ?",
                request.getSql());
        assertArrayEquals(new Object[]{1L}, request.getArgs());
    }

    @Test
    void whereShouldSupportMultipleConditionsAsAnd() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .where(
                        users.ID.eq(1L),
                        users.AGE.ge(18),
                        users.NAME.isNotNull()
                )
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT `users`.* FROM `users` WHERE (`users`.`id` = ? AND `users`.`age` >= ? AND `users`.`name` IS NOT NULL)",
                request.getSql());
        assertArrayEquals(new Object[]{1L, 18}, request.getArgs());
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

        assertEquals("SELECT `users`.* FROM `users` WHERE 1 = 0 AND 1 = 1", request.getSql());
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
                "SELECT COUNT(*) AS `total`, AVG(`users`.`age`) AS `avg_age`, MAX(`users`.`age`) AS `max_age`, MIN(`users`.`age`) AS `min_age` FROM `users`",
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
                "SELECT IF(`users`.`age` >= ?, ?, ?) AS `age_group` FROM `users`",
                mysqlRequest.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, mysqlRequest.getArgs());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group"))
                .from(users)
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT CASE WHEN \"users\".\"age\" >= ? THEN ? ELSE ? END AS \"age_group\" FROM \"users\"",
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
                "SELECT IF(IF(`users`.`age` >= ?, ?, ?) = ?, ?, ?) AS `adult_flag` FROM `users`",
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
                "SELECT IF(`users`.`age` >= ?, ?, ?) AS `age_group`, COUNT(*) AS `total` FROM `users` GROUP BY IF(`users`.`age` >= ?, ?, ?) HAVING COUNT(*) >= ? ORDER BY COUNT(*) DESC",
                request.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor", 18, "adult", "minor", 2}, request.getArgs());
    }

    @Test
    void orderByShouldSupportAliasReference() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(
                        Functions.count().as("total"),
                        Functions.max(users.AGE).as("max_age"))
                .from(users)
                .orderBy(order -> order.descAlias("total").ascAlias("max_age"))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS `total`, MAX(`users`.`age`) AS `max_age` FROM `users` ORDER BY `total` DESC, `max_age` ASC",
                mysqlRequest.getSql());
        assertArrayEquals(new Object[0], mysqlRequest.getArgs());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(
                        Functions.count().as("total"),
                        Functions.max(users.AGE).as("max_age"))
                .from(users)
                .orderBy(order -> order.descAlias("total").ascAlias("max_age"))
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT COUNT(*) AS \"total\", MAX(\"users\".\"age\") AS \"max_age\" FROM \"users\" ORDER BY \"total\" DESC, \"max_age\" ASC",
                postgresqlRequest.getSql());
        assertArrayEquals(new Object[0], postgresqlRequest.getArgs());
    }

    @Test
    void orderByShouldAcceptNamedExpressionDirectly() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(total)
                .from(users)
                .orderBy(order -> order.desc(total))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS `total` FROM `users` ORDER BY `total` DESC",
                request.getSql());
        assertArrayEquals(new Object[0], request.getArgs());
    }

    @Test
    void groupByShouldSupportAliasReference() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression ageGroup = Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group");

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(ageGroup, Functions.count().as("total"))
                .from(users)
                .groupBy(ageGroup)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(`users`.`age` >= ?, ?, ?) AS `age_group`, COUNT(*) AS `total` FROM `users` GROUP BY `age_group`",
                mysqlRequest.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, mysqlRequest.getArgs());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(ageGroup, Functions.count().as("total"))
                .from(users)
                .groupByAlias("age_group")
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT CASE WHEN \"users\".\"age\" >= ? THEN ? ELSE ? END AS \"age_group\", COUNT(*) AS \"total\" FROM \"users\" GROUP BY \"age_group\"",
                postgresqlRequest.getSql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, postgresqlRequest.getArgs());
    }

    @Test
    void havingShouldSupportAliasReference() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(total)
                .from(users)
                .having(having -> having.geAlias("total", 2))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS `total` FROM `users` HAVING `total` >= ?",
                mysqlRequest.getSql());
        assertArrayEquals(new Object[]{2}, mysqlRequest.getArgs());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(Wrapper.query()
                .select(total)
                .from(users)
                .having(having -> having.geAlias("total", 2))
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT COUNT(*) AS \"total\" FROM \"users\" HAVING \"total\" >= ?",
                postgresqlRequest.getSql());
        assertArrayEquals(new Object[]{2}, postgresqlRequest.getArgs());
    }

    @Test
    void havingShouldSupportDirectConditionShortcut() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(total)
                .from(users)
                .having(total.ge(2))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS `total` FROM `users` HAVING `total` >= ?",
                request.getSql());
        assertArrayEquals(new Object[]{2}, request.getArgs());
    }

    @Test
    void havingShouldSupportMultipleConditionsAsAnd() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");
        NamedSqlExpression maxAge = Functions.max(users.AGE).as("max_age");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(total, maxAge)
                .from(users)
                .having(total.ge(2), maxAge.ge(18))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS `total`, MAX(`users`.`age`) AS `max_age` FROM `users` HAVING (`total` >= ? AND `max_age` >= ?)",
                request.getSql());
        assertArrayEquals(new Object[]{2, 18}, request.getArgs());
    }

    @Test
    void namedExpressionShouldSupportDirectComparisonAndOrdering() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(total)
                .from(users)
                .having(having -> having.condition(total.ge(2)))
                .orderBy(order -> order.asc(total))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS `total` FROM `users` HAVING `total` >= ? ORDER BY `total` ASC",
                request.getSql());
        assertArrayEquals(new Object[]{2}, request.getArgs());
    }

    @Test
    void countRewriteShouldUseDialectCountRewriter() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.rewriteCount(Wrapper.query()
                .selectAll()
                .from(users)
                .where(where -> where.ge(users.AGE, 18))
                .orderBy(order -> order.asc(users.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "select count(*) from (SELECT `users`.* FROM `users` WHERE `users`.`age` >= ? ORDER BY `users`.`id` ASC) _kora_count",
                request.getSql());
        assertArrayEquals(new Object[]{18}, request.getArgs());
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
                "SELECT `users`.* FROM `users` WHERE `users`.`id` = ? OR (`users`.`name` = ? AND (`users`.`age` >= ? AND `users`.`age` < ?))",
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
                        .condition(users.ID.eq(alias.ID))
                        .or(or -> or
                                .eq(alias.NAME, "Bob")
                                .eq(alias.AGE, 30)))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT `users`.* FROM `users` LEFT JOIN `users` `u2` ON `users`.`id` = `u2`.`id` OR (`u2`.`name` = ? AND `u2`.`age` = ?)",
                request.getSql());
        assertArrayEquals(new Object[]{"Bob", 30}, request.getArgs());
    }

    @Test
    void joinShouldSupportShortcutOnClause() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable alias = new TestUserTable("users", "u2");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .selectAll()
                .from(users)
                .leftJoin(alias, on -> on.eq(users.ID, alias.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT `users`.* FROM `users` LEFT JOIN `users` `u2` ON `users`.`id` = `u2`.`id`",
                request.getSql());
        assertArrayEquals(new Object[0], request.getArgs());
    }

    @Test
    void tableAliasShouldBeConvenientForSelfJoin() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable manager = users.alias("manager");

        SqlRequest request = sqlGenerator.renderQuery(Wrapper.query()
                .select(users.NAME, manager.NAME)
                .from(users)
                .leftJoin(manager, on -> on.eq(users.ID, manager.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT `users`.`name`, `manager`.`name` FROM `users` LEFT JOIN `users` `manager` ON `users`.`id` = `manager`.`id`",
                request.getSql());
        assertArrayEquals(new Object[0], request.getArgs());
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
                "SELECT `users`.* FROM `users` WHERE `users`.`id` = ? AND (NOT (`users`.`name` = ? OR (`users`.`age` < ?)))",
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
                .on(users.ID.eq(innerAlias.ID))
                .rightJoin(rightAlias)
                .on(on -> on.eq(users.ID, rightAlias.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT `users`.* FROM `users` INNER JOIN `users` `u2` ON `users`.`id` = `u2`.`id` RIGHT JOIN `users` `u3` ON `users`.`id` = `u3`.`id`",
                request.getSql());
        assertArrayEquals(new Object[0], request.getArgs());
    }

    @Test
    void queryWrapperShouldExecuteWithBoundSqlSession() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        TestUserTable users = TestUserTable.USERS;

        QueryWrapper query = Wrapper.query(sqlSession)
                .selectAll()
                .from(users)
                .where(users.ID.eq(1L));

        TestUser one = query.one(TestUser.class);
        List<TestUser> list = query.list(TestUser.class);
        long count = query.count();
        Page<TestUser> page = query.page(Paging.of(1, 10), TestUser.class);

        assertEquals(sqlSession.oneResult, one);
        assertEquals(sqlSession.listResult, list);
        assertEquals(3L, count);
        assertEquals(3L, page.total());
        assertEquals(sqlSession.listResult, page.records());
    }

    @Test
    void queryWrapperShouldRejectExecutionWithoutSqlSession() {
        TestUserTable users = TestUserTable.USERS;
        QueryWrapper query = Wrapper.query()
                .selectAll()
                .from(users)
                .where(users.ID.eq(1L));

        assertThrows(SqlExecutorException.class, () -> query.one(TestUser.class));
        assertThrows(SqlExecutorException.class, query::count);
    }

    private static final class TestUser {
    }

    private static final class RecordingSqlExecutor implements SqlExecutor {
        private final DefaultSqlGenerator sqlGenerator = new DefaultSqlGenerator();
        private final com.nicleo.kora.core.runtime.SqlPagingSupport sqlPagingSupport = new com.nicleo.kora.core.runtime.SqlPagingSupport() {
            @Override
            public <T> Page<T> page(SqlExecutor sqlExecutor, com.nicleo.kora.core.runtime.SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType) {
                return new Page<>(paging.getCurrent(), paging.getSize(), 3L, listResult.stream().map(elementType::cast).toList());
            }

            @Override
            public long count(SqlExecutor sqlExecutor, com.nicleo.kora.core.runtime.SqlExecutionContext context, String sql, Object[] args) {
                return 3L;
            }
        };
        private final TypeConverter typeConverter = new TypeConverter();
        private final TestUser oneResult = new TestUser();
        private final List<TestUser> listResult = List.of(new TestUser(), new TestUser());

        @Override
        public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
            return resultType.cast(oneResult);
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, Class<T> resultType) {
            return listResult.stream().map(resultType::cast).toList();
        }

        @Override
        public int update(String sql, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
            return null;
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeConverter getTypeConverter() {
            return typeConverter;
        }

        @Override
        public void setTypeConverter(TypeConverter typeConverter) {
        }

        @Override
        public IdGenerator getIdGenerator() {
            return null;
        }

        @Override
        public void setIdGenerator(IdGenerator idGenerator) {

        }

        @Override
        public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            return selectOne(sql, args, resultType);
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            return selectList(sql, args, resultType);
        }

        @Override
        public int update(String sql, Object[] args, SqlExecutionContext context) {
            return update(sql, args);
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context) {
            return executeBatch(sql, batchArgs);
        }

        @Override
        public com.nicleo.kora.core.runtime.SqlPagingSupport getSqlPagingSupport() {
            return sqlPagingSupport;
        }

        @Override
        public DbType getDbType() {
            return DbType.MYSQL;
        }

        @Override
        public com.nicleo.kora.core.runtime.SqlGenerator getSqlGenerator() {
            return sqlGenerator;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
            if (sql.startsWith("select count(*)")) {
                return List.of((T) Long.valueOf(3L));
            }
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestUserTable extends EntityTable<TestUser> {
        private static final TestUserTable USERS = new TestUserTable("users", null);

        private final Column<TestUser, Long> ID = column("id", Long.class);
        private final Column<TestUser, String> NAME = column("name", String.class);
        private final Column<TestUser, Integer> AGE = column("age", Integer.class);

        private TestUserTable(String tableName, String alias) {
            super(TestUser.class, tableName, alias);
        }

        private TestUserTable alias(String alias) {
            return new TestUserTable(tableName(), alias);
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
