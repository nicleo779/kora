package org.byteora.kyra.orm.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlPagingSupportTest {
    private final DefaultSqlPagingSupport pagingSupport = new DefaultSqlPagingSupport();

    @Test
    void buildPageRequestShouldUseLimitOffsetForMySql() {
        SqlRequest request = pagingSupport.buildPageRequest(
                dbTypeExecutor(DbType.MYSQL),
                "select id from user where age >= ?",
                new Object[]{18},
                10,
                20
        );

        assertEquals("select id from user where age >= ? LIMIT ? OFFSET ?", request.sql());
        assertArrayEquals(new Object[]{18, 10, 20}, request.args());
    }

    @Test
    void buildPageRequestShouldUseOffsetFetchForSqlServer() {
        SqlRequest request = pagingSupport.buildPageRequest(
                dbTypeExecutor(DbType.SQLSERVER),
                "select id from user where age >= ?",
                new Object[]{18},
                10,
                20
        );

        assertEquals(
                "select id from user where age >= ? ORDER BY 1 OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                request.sql()
        );
        assertArrayEquals(new Object[]{18, 20, 10}, request.args());
    }

    @Test
    void buildCountRequestShouldRewriteSimpleSelectToDirectCount() {
        String sql = "select id, name from user where age >= ? order by id";

        assertEquals(
                "select count(*) from user where age >= ?",
                pagingSupport.buildCountRequest(dbTypeExecutor(DbType.MYSQL), sql, new Object[]{18}).sql()
        );
    }

    @Test
    void buildCountRequestShouldStripOrderByInSubqueryFallback() {
        String sql = "select age, count(*) from user group by age order by age";

        assertEquals(
                "select count(*) from (select age, count(*) from user group by age) _count",
                pagingSupport.buildCountRequest(dbTypeExecutor(DbType.MYSQL), sql, new Object[0]).sql()
        );
    }

    @Test
    void buildCountRequestShouldKeepLimitOffsetTailAfterStrippingOrderBy() {
        String sql = "select distinct name from user order by name limit ? offset ?";

        assertEquals(
                "select count(*) from (select distinct name from user limit ? offset ?) _count",
                pagingSupport.buildCountRequest(dbTypeExecutor(DbType.MYSQL), sql, new Object[]{10, 20}).sql()
        );
    }

    @Test
    void buildCountRequestShouldKeepOrderByWhenItContainsPlaceholder() {
        String sql = "select distinct name from user order by case when name = ? then 0 else 1 end";

        assertEquals(
                "select count(*) from (select distinct name from user order by case when name = ? then 0 else 1 end) _count",
                pagingSupport.buildCountRequest(dbTypeExecutor(DbType.MYSQL), sql, new Object[]{"a"}).sql()
        );
    }

    private static SqlExecutor dbTypeExecutor(DbType dbType) {
        return new SqlExecutor() {
            @Override
            public DbType getDbType() {
                return dbType;
            }

            @Override
            public SqlPagingSupport getSqlPagingSupport() {
                throw new UnsupportedOperationException();
            }

            @Override
            public SqlGenerator getSqlGenerator() {
                throw new UnsupportedOperationException();
            }

            @Override
            public TypeConverter getTypeConverter() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setTypeConverter(TypeConverter typeConverter) {
                throw new UnsupportedOperationException();
            }

            @Override
            public IdGenerator getIdGenerator() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setIdGenerator(IdGenerator idGenerator) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> java.util.List<T> selectList(String sql, Object[] args, Class<T> resultType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int update(String sql, Object[] args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int[] executeBatch(String sql, java.util.List<Object[]> batchArgs) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> java.util.List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int update(String sql, Object[] args, SqlExecutionContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int[] executeBatch(String sql, java.util.List<Object[]> batchArgs, SqlExecutionContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public <T> java.util.List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
