package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.runtime.jdbc.DefaultSqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultSqlSessionTest {
    private JdbcDataSource dataSource;
    private DefaultSqlSession sqlSession;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:kora;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        this.sqlSession = new DefaultSqlSession(dataSource);
        sqlSession.clearTypeConverters();
        GeneratedReflectors.install(new TestReflectorResolver());

        sqlSession.update("drop table if exists user_account", new Object[0]);
        sqlSession.update("create table user_account(id bigint primary key, user_name varchar(64), age int)", new Object[0]);
        sqlSession.update("insert into user_account(id, user_name, age) values(?, ?, ?)", new Object[]{1L, "Alice", 18});
        sqlSession.update("insert into user_account(id, user_name, age) values(?, ?, ?)", new Object[]{2L, "Bob", 20});
    }

    @Test
    void selectOneMapsColumnsThroughGeneratedReflector() {
        UserAccount user = sqlSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{1L},
                UserAccount.class
        );

        assertNotNull(user);
        assertEquals(1L, user.getId());
        assertEquals("Alice", user.getUserName());
        assertEquals(18, user.getAge());
    }

    @Test
    void dbTypeShouldBeInferredFromDataSource() {
        assertEquals(DbType.H2, sqlSession.getDbType());
    }

    @Test
    void selectListReturnsAllRows() {
        List<UserAccount> users = sqlSession.selectList(
                "select id, user_name, age from user_account order by id",
                new Object[0],
                UserAccount.class
        );

        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).getUserName());
        assertEquals("Bob", users.get(1).getUserName());
        assertThrows(UnsupportedOperationException.class, () -> users.add(new UserAccount()));
    }

    @Test
    void selectListReturnsSameReadOnlyListWhenCached() {
        List<UserAccount> first = sqlSession.selectList(
                "select id, user_name, age from user_account order by id",
                new Object[0],
                UserAccount.class
        );
        List<UserAccount> cached = sqlSession.selectList(
                "select id, user_name, age from user_account order by id",
                new Object[0],
                UserAccount.class
        );

        assertSame(first, cached);
        assertEquals(ImmutableArrayList.class, first.getClass());
    }

    @Test
    void selectOneReturnsNullWhenNoRowsExist() {
        UserAccount user = sqlSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{999L},
                UserAccount.class
        );

        assertNull(user);
    }

    @Test
    void selectOneUsesSessionCacheUntilItIsCleared() throws Exception {
        UserAccount first = sqlSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{1L},
                UserAccount.class
        );
        updateUserNameDirectly(1L, "Alicia");

        UserAccount cached = sqlSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{1L},
                UserAccount.class
        );

        assertSame(first, cached);
        assertEquals("Alice", cached.getUserName());

        sqlSession.clearCache();

        UserAccount refreshed = sqlSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{1L},
                UserAccount.class
        );

        assertNotSame(first, refreshed);
        assertEquals("Alicia", refreshed.getUserName());
    }

    @Test
    void updateClearsSessionCache() {
        UserAccount first = sqlSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{1L},
                UserAccount.class
        );

        sqlSession.update(
                "update user_account set user_name = ? where id = ?",
                new Object[]{"Alicia", 1L}
        );

        UserAccount refreshed = sqlSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{1L},
                UserAccount.class
        );

        assertNotSame(first, refreshed);
        assertEquals("Alicia", refreshed.getUserName());
    }

    @Test
    void interceptorCanRewriteSqlArgumentsAndSeeContext() {
        List<SqlExecutionContext> contexts = new ArrayList<>();
        DefaultSqlSession interceptedSession = new DefaultSqlSession(dataSource);
        interceptedSession.addInterceptor((context, request) -> {
            contexts.add(context);
            return request.withArgs(new Object[]{2L});
        });
        UserAccount user = interceptedSession.selectOne(
                "select id, user_name, age from user_account where id = ?",
                new Object[]{1L},
                new SqlExecutionContext(interceptedSession, "demo.Mapper", "selectById", "selectById", com.nicleo.kora.core.xml.SqlCommandType.SELECT, UserAccount.class, true),
                UserAccount.class
        );

        assertNotNull(user);
        assertEquals(2L, user.getId());
        assertEquals(1, contexts.size());
        assertSame(interceptedSession, contexts.get(0).getSqlSession());
        assertEquals("demo.Mapper", contexts.get(0).getMapperClassName());
        assertEquals("selectById", contexts.get(0).getMapperMethodName());
        assertEquals(UserAccount.class, contexts.get(0).getResultType());
    }

    @Test
    void interceptorCanUseCurrentSessionForCountQuery() {
        List<Long> totals = new ArrayList<>();
        DefaultSqlSession interceptedSession = getDefaultSqlSession(totals);
        List<UserAccount> users = interceptedSession.selectList(
                "select id, user_name, age from user_account order by id",
                new Object[0],
                new SqlExecutionContext(interceptedSession, "demo.Mapper", "pagedQuery", "pagedQuery", com.nicleo.kora.core.xml.SqlCommandType.SELECT, UserAccount.class, true),
                UserAccount.class
        );

        assertEquals(2, users.size());
        assertEquals(List.of(2L), totals);
    }

    private DefaultSqlSession getDefaultSqlSession(List<Long> totals) {
        DefaultSqlSession interceptedSession = new DefaultSqlSession(dataSource);
        interceptedSession.addInterceptor((context, request) -> {
            if ("pagedQuery".equals(context.getStatementId())) {
                CountResult total = context.getSqlSession().selectOne(
                        "select count(*) as total from user_account",
                        new Object[0],
                        context.withoutInterceptors(),
                        CountResult.class
                );
                totals.add(total.getTotal());
            }
            return request;
        });
        return interceptedSession;
    }

    @Test
    void customTypeConverterCanMapJdbcColumnValueToEntityFieldType() {
        sqlSession.update("alter table user_account add column created_at timestamp", new Object[0]);
        sqlSession.update(
                "update user_account set created_at = ? where id = ?",
                new Object[]{Timestamp.valueOf(LocalDateTime.of(2024, 1, 2, 3, 4, 5)), 1L}
        );
        sqlSession.registerTypeConverter(new CustomTypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == LocalDateTime.class;
            }

            @Override
            public Object fromDb(Object value, Class<?> targetType) {
                return ((Timestamp) value).toLocalDateTime();
            }

            @Override
            public Object toDb(Object value, Class<?> targetType) {
                return Timestamp.valueOf((LocalDateTime) value);
            }
        });

        TimeUserAccount user = sqlSession.selectOne(
                "select id, user_name, age, created_at from user_account where id = ?",
                new Object[]{1L},
                TimeUserAccount.class
        );

        assertNotNull(user);
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), user.getCreatedAt());
    }

    @Test
    void typeConvertersShouldBeScopedPerSession() {
        sqlSession.update("alter table user_account add column created_at timestamp", new Object[0]);
        sqlSession.update(
                "update user_account set created_at = ? where id = ?",
                new Object[]{Timestamp.valueOf(LocalDateTime.of(2024, 1, 2, 3, 4, 5)), 1L}
        );
        sqlSession.registerTypeConverter(new CustomTypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == LocalDateTime.class;
            }

            @Override
            public Object fromDb(Object value, Class<?> targetType) {
                return ((Timestamp) value).toLocalDateTime();
            }

            @Override
            public Object toDb(Object value, Class<?> targetType) {
                return Timestamp.valueOf((LocalDateTime) value);
            }
        });
        DefaultSqlSession isolatedSession = new DefaultSqlSession(dataSource);

        TimeUserAccount converted = sqlSession.selectOne(
                "select id, user_name, age, created_at from user_account where id = ?",
                new Object[]{1L},
                TimeUserAccount.class
        );
        assertNotNull(converted);
        assertEquals(LocalDateTime.of(2024, 1, 2, 3, 4, 5), converted.getCreatedAt());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> isolatedSession.selectOne(
                "select id, user_name, age, created_at from user_account where id = ?",
                new Object[]{1L},
                TimeUserAccount.class
        ));
        if (ex instanceof SqlSessionException sqlSessionException) {
            assertEquals(ClassCastException.class, sqlSessionException.getCause().getClass());
        } else {
            assertEquals(ClassCastException.class, ex.getClass());
        }
    }

    @Test
    void customTypeConverterShouldConvertFieldValueToDbOnWrite() {
        sqlSession.update("alter table user_account add column created_at timestamp", new Object[0]);
        sqlSession.registerTypeConverter(new CustomTypeConverter() {
            @Override
            public boolean supports(Class<?> targetType) {
                return targetType == LocalDateTime.class;
            }

            @Override
            public Object fromDb(Object value, Class<?> targetType) {
                return ((Timestamp) value).toLocalDateTime();
            }

            @Override
            public Object toDb(Object value, Class<?> targetType) {
                return Timestamp.valueOf((LocalDateTime) value);
            }
        });

        LocalDateTime createdAt = LocalDateTime.of(2025, 1, 2, 3, 4, 5);
        sqlSession.update(
                "update user_account set created_at = ? where id = ?",
                new Object[]{createdAt, 1L}
        );

        TimeUserAccount user = sqlSession.selectOne(
                "select id, user_name, age, created_at from user_account where id = ?",
                new Object[]{1L},
                TimeUserAccount.class
        );

        assertNotNull(user);
        assertEquals(createdAt, user.getCreatedAt());
    }

    @Test
    void aliasAnnotationInFieldInfoCanMapAliasedColumns() {
        UserAccount user = sqlSession.selectOne(
                "select id, user_name as login_name, age from user_account where id = ?",
                new Object[]{1L},
                UserAccount.class
        );

        assertNotNull(user);
        assertEquals(1L, user.getId());
        assertEquals("Alice", user.getUserName());
        assertEquals(18, user.getAge());
    }

    @Test
    void sessionShouldReuseSingleConnectionUntilClosed() {
        AtomicInteger openCount = new AtomicInteger();
        DefaultSqlSession session = new DefaultSqlSession(countingDataSource(openCount));
        GeneratedReflectors.install(new TestReflectorResolver());

        session.update("drop table if exists user_account", new Object[0]);
        session.update("create table user_account(id bigint primary key, user_name varchar(64), age int)", new Object[0]);
        session.update("insert into user_account(id, user_name, age) values(?, ?, ?)", new Object[]{1L, "Alice", 18});
        session.selectOne("select id, user_name, age from user_account where id = ?", new Object[]{1L}, UserAccount.class);
        session.selectList("select id, user_name, age from user_account", new Object[0], UserAccount.class);

        assertEquals(1, openCount.get());

        session.close();
    }

    @Test
    void closedSessionShouldRejectFurtherOperations() {
        sqlSession.close();

        SqlSessionException ex = assertThrows(SqlSessionException.class, () ->
                sqlSession.selectOne("select id, user_name, age from user_account where id = ?", new Object[]{1L}, UserAccount.class)
        );
        assertTrue(ex.getMessage().contains("closed"));
    }

    private void updateUserNameDirectly(long id, String userName) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update user_account set user_name = ? where id = ?"
             )) {
            statement.setString(1, userName);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
    }

    private DataSource countingDataSource(AtomicInteger openCount) {
        JdbcDataSource delegate = new JdbcDataSource();
        delegate.setURL("jdbc:h2:mem:kora_session;MODE=MySQL;DB_CLOSE_DELAY=-1");
        delegate.setUser("sa");
        delegate.setPassword("");
        return new DataSource() {
            @Override
            public Connection getConnection() throws java.sql.SQLException {
                openCount.incrementAndGet();
                return delegate.getConnection();
            }

            @Override
            public Connection getConnection(String user, String password) throws java.sql.SQLException {
                openCount.incrementAndGet();
                return delegate.getConnection(user, password);
            }

            @Override
            public PrintWriter getLogWriter() throws java.sql.SQLException {
                return delegate.getLogWriter();
            }

            @Override
            public void setLogWriter(PrintWriter out) throws java.sql.SQLException {
                delegate.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws java.sql.SQLException {
                delegate.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws java.sql.SQLException {
                return delegate.getLoginTimeout();
            }

            @Override
            public Logger getParentLogger() {
                return Logger.getGlobal();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws java.sql.SQLException {
                return delegate.unwrap(iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws java.sql.SQLException {
                return delegate.isWrapperFor(iface);
            }
        };
    }

    static final class TestReflectorResolver implements GeneratedReflectors.Resolver {
        @Override
        public <T> GeneratedReflector<T> get(Class<T> type) {
            if (type == UserAccount.class) {
                return (GeneratedReflector<T>) new UserAccountGeneratedReflector();
            }
            if (type == TimeUserAccount.class) {
                return (GeneratedReflector<T>) new TimeUserAccountGeneratedReflector();
            }
            if (type == CountResult.class) {
                return (GeneratedReflector<T>) new CountResultGeneratedReflector();
            }
            throw new SqlSessionException("No test reflector for type: " + type.getName());
        }
    }

    static final class UserAccount {
        private long id;
        private String userName;
        private int age;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    static final class CountResult {
        private Long total;

        public Long getTotal() {
            return total;
        }

        public void setTotal(Long total) {
            this.total = total;
        }
    }

    static final class TimeUserAccount {
        private long id;
        private String userName;
        private int age;
        private LocalDateTime createdAt;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    static final class UserAccountGeneratedReflector implements GeneratedReflector<UserAccount> {
        private static final FieldInfo[] FIELDS = new FieldInfo[]{
                new FieldInfo("id", Long.class, 0, null, new java.lang.annotation.Annotation[0]),
                new FieldInfo("userName", String.class, 0, "login_name", new java.lang.annotation.Annotation[0]),
                new FieldInfo("age", Integer.class, 0, null, new java.lang.annotation.Annotation[0])
        };

        @Override
        public UserAccount newInstance() {
            return new UserAccount();
        }

        @Override
        public Object invoke(UserAccount target, String method, Object[] args) {
            return switch (method) {
                case "getId" -> target.getId();
                case "getUserName" -> target.getUserName();
                case "getAge" -> target.getAge();
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
        }

        @Override
        public void set(UserAccount target, String property, Object value) {
            switch (property) {
                case "id" -> target.setId((Long) value);
                case "userName" -> target.setUserName((String) value);
                case "age" -> target.setAge((Integer) value);
                default -> {
                }
            }
        }

        @Override
        public Object get(UserAccount target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "userName" -> target.getUserName();
                case "age" -> target.getAge();
                default -> throw new IllegalArgumentException("Unknown property: " + property);
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "userName", "age"};
        }

        @Override
        public FieldInfo getField(String field) {
            return switch (field) {
                case "id" -> FIELDS[0];
                case "userName" -> FIELDS[1];
                case "age" -> FIELDS[2];
                default -> null;
            };
        }
    }

    static final class CountResultGeneratedReflector implements GeneratedReflector<CountResult> {
        private static final FieldInfo[] FIELDS = new FieldInfo[]{
                new FieldInfo("total", Long.class, 0, null, new java.lang.annotation.Annotation[0])
        };

        @Override
        public CountResult newInstance() {
            return new CountResult();
        }

        @Override
        public Object invoke(CountResult target, String method, Object[] args) {
            return switch (method) {
                case "getTotal" -> target.getTotal();
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
        }

        @Override
        public void set(CountResult target, String property, Object value) {
            switch (property) {
                case "total" -> target.setTotal((Long) value);
                default -> {
                }
            }
        }

        @Override
        public Object get(CountResult target, String property) {
            return switch (property) {
                case "total" -> target.getTotal();
                default -> throw new IllegalArgumentException("Unknown property: " + property);
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"total"};
        }

        @Override
        public FieldInfo getField(String field) {
            return "total".equals(field) ? FIELDS[0] : null;
        }
    }

    static final class TimeUserAccountGeneratedReflector implements GeneratedReflector<TimeUserAccount> {
        private static final FieldInfo[] FIELDS = new FieldInfo[]{
                new FieldInfo("id", Long.class, 0, null, new java.lang.annotation.Annotation[0]),
                new FieldInfo("userName", String.class, 0, "login_name", new java.lang.annotation.Annotation[0]),
                new FieldInfo("age", Integer.class, 0, null, new java.lang.annotation.Annotation[0]),
                new FieldInfo("createdAt", LocalDateTime.class, 0, null, new java.lang.annotation.Annotation[0])
        };

        @Override
        public TimeUserAccount newInstance() {
            return new TimeUserAccount();
        }

        @Override
        public Object invoke(TimeUserAccount target, String method, Object[] args) {
            return switch (method) {
                case "getId" -> target.getId();
                case "getUserName" -> target.getUserName();
                case "getAge" -> target.getAge();
                case "getCreatedAt" -> target.getCreatedAt();
                default -> throw new IllegalArgumentException("Unknown method: " + method);
            };
        }

        @Override
        public void set(TimeUserAccount target, String property, Object value) {
            switch (property) {
                case "id" -> target.setId((Long) value);
                case "userName" -> target.setUserName((String) value);
                case "age" -> target.setAge((Integer) value);
                case "createdAt" -> target.setCreatedAt((LocalDateTime) value);
                default -> {
                }
            }
        }

        @Override
        public Object get(TimeUserAccount target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "userName" -> target.getUserName();
                case "age" -> target.getAge();
                case "createdAt" -> target.getCreatedAt();
                default -> throw new IllegalArgumentException("Unknown property: " + property);
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "userName", "age", "createdAt"};
        }

        @Override
        public FieldInfo getField(String field) {
            return switch (field) {
                case "id" -> FIELDS[0];
                case "userName" -> FIELDS[1];
                case "age" -> FIELDS[2];
                case "createdAt" -> FIELDS[3];
                default -> null;
            };
        }
    }

}
