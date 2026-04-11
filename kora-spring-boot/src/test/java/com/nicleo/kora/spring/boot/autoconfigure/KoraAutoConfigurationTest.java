package com.nicleo.kora.spring.boot.autoconfigure;

import com.nicleo.kora.core.runtime.DefaultSqlPagingSupport;
import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionFactory;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.query.QueryWrapper;
import com.nicleo.kora.core.query.Tables;
import com.nicleo.kora.core.query.Wrapper;
import com.nicleo.kora.core.annotation.KoraScan;
import com.nicleo.kora.spring.boot.autoconfigure.collision.CollisionMapperKoraConfig;
import com.nicleo.kora.spring.boot.autoconfigure.mapper.TestUser;
import com.nicleo.kora.spring.boot.autoconfigure.mapper.TestMapperKoraConfig;
import com.nicleo.kora.spring.boot.autoconfigure.mapper.TestUserMapper;
import com.nicleo.kora.spring.boot.CurrentSqlSessionProvider;
import com.nicleo.kora.spring.boot.SpringTransactionSqlSession;
import com.nicleo.kora.spring.boot.Sql;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KoraAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KoraAutoConfiguration.class));

    @Test
    void shouldAutoConfigureSqlSessionFromDataSource() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .run(context -> {
                    SqlSession first = context.getBean(SqlSession.class);
                    SqlSession second = context.getBean(SqlSession.class);
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    SqlSession third = factory.openSession();
                    assertInstanceOf(SpringTransactionSqlSession.class, first);
                    assertNotSame(first, second);
                    assertNotSame(first, third);
                    assertInstanceOf(DefaultSqlPagingSupport.class, context.getBean(SqlPagingSupport.class));
                    third.close();
                });
    }

    @Test
    void shouldUseCustomPagingSupportAndInterceptors() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class, CustomBeansConfig.class)
                .run(context -> {
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);
                    SpringTransactionSqlSession sqlSession = (SpringTransactionSqlSession) factory.openSession();
                    SqlPagingSupport pagingSupport = context.getBean(SqlPagingSupport.class);
                    assertSame(pagingSupport, sqlSession.getSqlPagingSupport());
                    assertSame(context.getBean("testInterceptor"), sqlSession.getInterceptors().getFirst());
                    sqlSession.close();
                });
    }

    @Test
    void shouldParticipateInSpringTransactionRollback() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class, TransactionConfig.class)
                .run(context -> {
                    GeneratedReflectors.clear();
                    GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> GeneratedReflector<T> get(Class<T> type) {
                            if (type == TxRow.class) {
                                return (GeneratedReflector<T>) new TxRowReflector();
                            }
                            throw new IllegalArgumentException(type.getName());
                        }
                    });

                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("drop table if exists tx_demo");
                        statement.execute("create table tx_demo(id bigint primary key, name varchar(32))");
                    }

                    TransactionTemplate template = context.getBean(TransactionTemplate.class);
                    SqlSessionFactory factory = context.getBean(SqlSessionFactory.class);

                    try {
                        template.executeWithoutResult(status -> {
                            try (SqlSession session = factory.openSession()) {
                                session.update("insert into tx_demo(id, name) values(?, ?)", new Object[]{1L, "demo"});
                            }
                            throw new RuntimeException("rollback");
                        });
                    } catch (RuntimeException ignored) {
                    }

                    try (SqlSession session = factory.openSession()) {
                        TxRow row = session.selectOne("select id, name from tx_demo where id = ?", new Object[]{1L}, TxRow.class);
                        assertNull(row);
                    }
                });
    }

    @Test
    void shouldSupportStaticSqlQueryDslWithRegisteredTableLookup() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .run(context -> {
                    GeneratedReflectors.clear();
                    GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> GeneratedReflector<T> get(Class<T> type) {
                            if (type == TxRow.class) {
                                return (GeneratedReflector<T>) new TxRowReflector();
                            }
                            throw new IllegalArgumentException(type.getName());
                        }
                    });

                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("drop table if exists tx_demo");
                        statement.execute("create table tx_demo(id bigint primary key, name varchar(32))");
                        statement.execute("insert into tx_demo(id, name) values (1, 'a'), (2, 'b')");
                    }

                    QueryWrapper oneQuery = Wrapper.<TxRow>query()
                            .selectAll()
                            .from(TxRowTable.TX_DEMO)
                            .orderBy(order -> order.asc(TxRowTable.TX_DEMO.id))
                            .limit(1);

                    TxRow one = Sql.query()
                            .selectAll()
                            .from(TxRowTable.TX_DEMO)
                            .orderBy(order -> order.asc(TxRowTable.TX_DEMO.id))
                            .limit(1)
                            .one(TxRow.class);
                    assertEquals(1L, one.getId());
                    one = Sql.select(TxRowTable.TX_DEMO, TxRowTable.TX_DEMO.id.eq(1));
                    assertEquals(1, one.getId());
                    Paging paging = new Paging();
                    paging.setCurrent(1);
                    paging.setSize(1);
                    Page<TxRow> page = Sql.query()
                            .selectAll()
                            .from(TxRowTable.TX_DEMO)
                            .orderBy(order -> order.asc(TxRowTable.TX_DEMO.id))
                            .page(paging, TxRow.class);
                    assertEquals(1, page.current());
                    assertEquals(2L, page.total());
                    assertEquals(1, page.records().size());
                });
    }

    @Test
    void shouldSupportStaticSqlFromShortcut() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .run(context -> {
                    GeneratedReflectors.clear();
                    GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> GeneratedReflector<T> get(Class<T> type) {
                            if (type == TxRow.class) {
                                return (GeneratedReflector<T>) new TxRowReflector();
                            }
                            throw new IllegalArgumentException(type.getName());
                        }
                    });

                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("drop table if exists tx_demo");
                        statement.execute("create table tx_demo(id bigint primary key, name varchar(32))");
                        statement.execute("insert into tx_demo(id, name) values (1, 'a'), (2, 'b')");
                    }

                    TxRow one = Sql.from(TxRowTable.TX_DEMO)
                            .orderBy(order -> order.asc(TxRowTable.TX_DEMO.id))
                            .limit(1)
                            .one(TxRow.class);

                    assertEquals(1L, one.getId());
                });
    }

    @Test
    void shouldSupportStaticSqlQueryDsl() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .run(context -> {
                    GeneratedReflectors.clear();
                    GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> GeneratedReflector<T> get(Class<T> type) {
                            if (type == TxRow.class) {
                                return (GeneratedReflector<T>) new TxRowReflector();
                            }
                            throw new IllegalArgumentException(type.getName());
                        }
                    });
                    Tables.register(TxRow.class, TxRowTable.TX_DEMO);

                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("drop table if exists tx_demo");
                        statement.execute("create table tx_demo(id bigint primary key, name varchar(32))");
                        statement.execute("insert into tx_demo(id, name) values (1, 'a'), (2, 'b')");
                    }

                    TxRow one = Sql.query()
                            .selectAll()
                            .from(TxRowTable.TX_DEMO)
                            .orderBy(order -> order.asc(TxRowTable.TX_DEMO.id))
                            .limit(1)
                            .one(TxRow.class);

                    assertEquals(1L, one.getId());
                });
    }

    @Test
    void shouldNotCloseReusedCurrentSqlSession() {
        var query = Wrapper.query()
                .selectAll()
                .from(TxRowTable.TX_DEMO)
                .limit(1);

        TrackingSqlSession sqlSession = new TrackingSqlSession();
        Sql.bind(() -> new CurrentSqlSessionProvider.SessionHandle(sqlSession, false));
        try {
            assertThrows(com.nicleo.kora.core.runtime.SqlSessionException.class, () -> Sql.query().selectAll().from(TxRowTable.TX_DEMO).one(TxRow.class));
            assertEquals(0, sqlSession.closeCount);
        } finally {
            Sql.clear();
        }
    }

    @Test
    void shouldClearStaticSqlBindingWhenContextCloses() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .run(context -> {
                    assertThrows(com.nicleo.kora.core.runtime.SqlSessionException.class, () -> {
                        context.close();
                        Sql.query();
                    });
                });
    }

    @Test
    void shouldSupportStaticSqlCrudHelpers() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class)
                .run(context -> {
                    GeneratedReflectors.clear();
                    GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
                        @Override
                        @SuppressWarnings("unchecked")
                        public <T> GeneratedReflector<T> get(Class<T> type) {
                            if (type == TxRow.class) {
                                return (GeneratedReflector<T>) new TxRowReflector();
                            }
                            throw new IllegalArgumentException(type.getName());
                        }
                    });
                    Tables.register(TxRow.class, TxRowTable.TX_DEMO);

                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("drop table if exists tx_demo");
                        statement.execute("create table tx_demo(id bigint auto_increment primary key, name varchar(32))");
                    }

                    TxRow inserted = new TxRow();
                    inserted.setName("demo");
                    assertEquals(1, Sql.insert(inserted));

                    TxRow update = new TxRow();
                    update.setId(1L);
                    update.setName("demo-updated");
                    assertEquals(1, Sql.updateById(update));

                    assertEquals(1, Sql.deleteById(TxRow.class, 1L));
                });
    }

    @Test
    void shouldRegisterMapperBeansIntoSpringContainer() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class, TestMapperKoraConfig.class)
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("drop table if exists test_user");
                        statement.execute("create table test_user(id bigint auto_increment primary key, name varchar(32))");
                        statement.execute("insert into test_user(name) values ('spring-user')");
                    }

                    TestUserMapper mapper = context.getBean(TestUserMapper.class);
                    TestUser user = mapper.selectById(1L);

                    assertEquals("spring-user", user.getName());
                });
    }

    @Test
    void shouldRegisterMappersWithSameSimpleNameUsingQualifiedBeanNames() {
        contextRunner
                .withUserConfiguration(DataSourceConfig.class, CollisionMapperKoraConfig.class)
                .run(context -> {
                    DataSource dataSource = context.getBean(DataSource.class);
                    try (var connection = dataSource.getConnection();
                         var statement = connection.createStatement()) {
                        statement.execute("drop table if exists left_user");
                        statement.execute("drop table if exists right_user");
                        statement.execute("create table left_user(id bigint primary key, name varchar(32))");
                        statement.execute("create table right_user(id bigint primary key, name varchar(32))");
                        statement.execute("insert into left_user(id, name) values (1, 'left-user')");
                        statement.execute("insert into right_user(id, name) values (1, 'right-user')");
                    }

                    var leftMapper = context.getBean(com.nicleo.kora.spring.boot.autoconfigure.collision.left.UserMapper.class);
                    var rightMapper = context.getBean(com.nicleo.kora.spring.boot.autoconfigure.collision.right.UserMapper.class);
                    var leftBean = context.getBean("com.nicleo.kora.spring.boot.autoconfigure.collision.left.UserMapper");
                    var rightBean = context.getBean("com.nicleo.kora.spring.boot.autoconfigure.collision.right.UserMapper");

                    assertTrue(leftBean instanceof com.nicleo.kora.spring.boot.autoconfigure.collision.left.UserMapper);
                    assertTrue(rightBean instanceof com.nicleo.kora.spring.boot.autoconfigure.collision.right.UserMapper);
                    assertEquals("left-user", leftMapper.selectById(1L).getName());
                    assertEquals("right-user", rightMapper.selectById(1L).getName());
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class DataSourceConfig {
        @Bean
        DataSource dataSource() {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.h2.Driver");
            dataSource.setUrl("jdbc:h2:mem:kora-spring;MODE=MySQL;DB_CLOSE_DELAY=-1");
            dataSource.setUsername("sa");
            dataSource.setPassword("");
            return dataSource;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomBeansConfig {
        @Bean
        SqlPagingSupport customPagingSupport() {
            return new DefaultSqlPagingSupport();
        }

        @Bean
        SqlInterceptor testInterceptor() {
            return (context, request) -> request;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TransactionConfig {
        @Bean
        DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        TransactionTemplate transactionTemplate(DataSourceTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    static final class TxRow {
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    static final class TxRowTable extends com.nicleo.kora.core.query.EntityTable<TxRow> {
        static final TxRowTable TX_DEMO = new TxRowTable();

        final com.nicleo.kora.core.query.Column<TxRow, Long> id = column("id", Long.class);
        final com.nicleo.kora.core.query.Column<TxRow, String> name = column("name", String.class);

        private TxRowTable() {
            super(TxRow.class, "tx_demo");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <V> com.nicleo.kora.core.query.Column<TxRow, V> idColumn() {
            return (com.nicleo.kora.core.query.Column<TxRow, V>) id;
        }

        @Override
        public String fieldName(String column) {
            return switch (column) {
                case "id" -> "id";
                case "name" -> "name";
                default -> column;
            };
        }

        @Override
        public String columnName(String field) {
            return switch (field) {
                case "id" -> "id";
                case "name" -> "name";
                default -> field;
            };
        }
    }

    static final class TxRowReflector implements GeneratedReflector<TxRow> {
        @Override
        public TxRow newInstance() {
            return new TxRow();
        }

        @Override
        public Object invoke(TxRow target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(TxRow target, String property, Object value) {
            switch (property) {
                case "id" -> target.setId((Long) value);
                case "name" -> target.setName((String) value);
                default -> {
                }
            }
        }

        @Override
        public Object get(TxRow target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "name" -> target.getName();
                default -> null;
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "name"};
        }

        @Override
        public FieldInfo getField(String field) {
            return switch (field) {
                case "id" -> new FieldInfo("id", Long.class, 0, null, new java.lang.annotation.Annotation[0]);
                case "name" -> new FieldInfo("name", String.class, 0, null, new java.lang.annotation.Annotation[0]);
                default -> null;
            };
        }
    }

    static final class TrackingSqlSession implements SqlSession {
        int closeCount;

        @Override
        public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
            throw new com.nicleo.kora.core.runtime.SqlSessionException("test");
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
        public int[] executeBatch(String sql, java.util.List<Object[]> batchArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.nicleo.kora.core.runtime.TypeConverter getTypeConverter() {
            return new com.nicleo.kora.core.runtime.TypeConverter();
        }

        @Override
        public void setTypeConverter(com.nicleo.kora.core.runtime.TypeConverter typeConverter) {
        }

        @Override
        public <T> java.util.List<T> executeQuery(String sql, Object[] args, com.nicleo.kora.core.runtime.RowMapper<T> rowMapper) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void close() {
            closeCount++;
        }
    }
}
