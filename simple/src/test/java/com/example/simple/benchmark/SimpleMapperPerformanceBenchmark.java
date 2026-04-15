package com.example.simple.benchmark;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.simple.entity.User;
import com.example.simple.mapper.UserMapper;
import com.example.simple.mapper.UserMapperImpl;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlExecutor;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.dialect.H2Dialect;
import org.babyfish.jimmer.sql.runtime.ConnectionManager;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Query;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.jooq.conf.Settings;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SimpleMapperPerformanceBenchmark {
    private static final org.jooq.Table<?> USERS = DSL.table(DSL.name("users"));
    private static final Field<Long> USER_ID = DSL.field(DSL.name("id"), Long.class);
    private static final Field<String> USER_NAME = DSL.field(DSL.name("name"), String.class);
    private static final Field<Integer> USER_AGE = DSL.field(DSL.name("age"), Integer.class);
    private static final Field<String> USER_LOGIN_NAME = DSL.field(DSL.name("login_name"), String.class);

    @Benchmark
    public User koraSelectById(BenchmarkState state) {
        return state.koraMapper.selectById(state.selectId);
    }

    @Benchmark
    public User myBatisSelectById(BenchmarkState state) {
        try (SqlSession session = state.myBatisFactory.openSession()) {
            return session.getMapper(MyBatisUserMapper.class).selectById(state.selectId);
        }
    }

    @Benchmark
    public PlusUser myBatisPlusSelectById(BenchmarkState state) {
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            return session.getMapper(MyBatisPlusUserMapper.class).selectById(state.selectId);
        }
    }

    @Benchmark
    public JooqUser jooqSelectById(BenchmarkState state) {
        return state.jooq.select(USER_ID, USER_NAME, USER_AGE, USER_LOGIN_NAME)
                .from(USERS)
                .where(USER_ID.eq(state.selectId))
                .fetchOne(SimpleMapperPerformanceBenchmark::toJooqUser);
    }

    @Benchmark
    public JimmerUser jimmerSelectById(BenchmarkState state) {
        return state.jimmerSqlClient.findById(JimmerUser.class, state.selectId);
    }

    @Benchmark
    public int koraInsertOne(BenchmarkState state) {
        return state.koraMapper.insert(state.newKoraUser(state.koraInsertId.incrementAndGet()));
    }

    @Benchmark
    public int myBatisPlusInsertOne(BenchmarkState state) {
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            int result = session.getMapper(MyBatisPlusUserMapper.class).insert(state.newPlusUser(state.plusInsertId.incrementAndGet()));
            session.commit();
            return result;
        }
    }

    @Benchmark
    public int jooqInsertOne(BenchmarkState state) {
        long id = state.jooqInsertId.incrementAndGet();
        return state.jooq.insertInto(USERS)
                .columns(USER_ID, USER_NAME, USER_AGE, USER_LOGIN_NAME)
                .values(id, "user-" + id, 18 + (int) (id % 40), "login-" + id)
                .execute();
    }

    @Benchmark
    public int jimmerInsertOne(BenchmarkState state) {
        return state.jimmerSqlClient.saveCommand(state.newJimmerUser(state.jimmerInsertId.incrementAndGet()))
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getTotalAffectedRowCount();
    }

    @Benchmark
    public List<User> koraSelectByAgeRange(BenchmarkState state) {
        return state.koraMapper.selectByAgeRange(state.minAge, state.maxAge);
    }

    @Benchmark
    public List<User> myBatisSelectByAgeRange(BenchmarkState state) {
        try (SqlSession session = state.myBatisFactory.openSession()) {
            return session.getMapper(MyBatisUserMapper.class).selectByAgeRange(state.minAge, state.maxAge);
        }
    }

    @Benchmark
    public List<PlusUser> myBatisPlusSelectByAgeRange(BenchmarkState state) {
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            return session.getMapper(MyBatisPlusUserMapper.class).selectList(
                    new LambdaQueryWrapper<PlusUser>()
                            .ge(PlusUser::getAge, state.minAge)
                            .le(PlusUser::getAge, state.maxAge)
                            .orderByAsc(PlusUser::getId)
            );
        }
    }

    @Benchmark
    public List<JooqUser> jooqSelectByAgeRange(BenchmarkState state) {
        return state.jooq.select(USER_ID, USER_NAME, USER_AGE, USER_LOGIN_NAME)
                .from(USERS)
                .where(USER_AGE.ge(state.minAge))
                .and(USER_AGE.le(state.maxAge))
                .orderBy(USER_ID.asc())
                .fetch(SimpleMapperPerformanceBenchmark::toJooqUser);
    }

    @Benchmark
    public List<JimmerUser> jimmerSelectByAgeRange(BenchmarkState state) {
        JimmerUserTable table = JimmerUserTable.$;
        return state.jimmerSqlClient.createQuery(table)
                .where(table.age().ge(state.minAge))
                .where(table.age().le(state.maxAge))
                .orderBy(table.id())
                .select(table.fetch(JimmerUserFetcher.$.allScalarFields()))
                .execute();
    }

    @Benchmark
    public int koraUpdateById(BenchmarkState state, UpdateTargetState targets) {
        long id = targets.koraUpdateId;
        User user = state.newKoraUser(id);
        user.setName("updated-" + id);
        return state.koraMapper.updateById(user);
    }

    @Benchmark
    public int myBatisPlusUpdateById(BenchmarkState state, UpdateTargetState targets) {
        long id = targets.plusUpdateId;
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            PlusUser user = state.newPlusUser(id);
            user.setName("updated-" + id);
            int result = session.getMapper(MyBatisPlusUserMapper.class).updateById(user);
            session.commit();
            return result;
        }
    }

    @Benchmark
    public int jooqUpdateById(BenchmarkState state, UpdateTargetState targets) {
        long id = targets.jooqUpdateId;
        return state.jooq.update(USERS)
                .set(USER_NAME, "updated-" + id)
                .set(USER_AGE, 18 + (int) (id % 40))
                .set(USER_LOGIN_NAME, "login-" + id)
                .where(USER_ID.eq(id))
                .execute();
    }

    @Benchmark
    public int jimmerUpdateById(BenchmarkState state, UpdateTargetState targets) {
        long id = targets.jimmerUpdateId;
        JimmerUser entity = state.newJimmerUser(id, "updated-" + id);
        return state.jimmerSqlClient.saveCommand(entity)
                .setMode(SaveMode.UPDATE_ONLY)
                .execute()
                .getTotalAffectedRowCount();
    }

    @Benchmark
    public int koraDeleteById(BenchmarkState state, DeleteTargetState targets) {
        return state.koraMapper.deleteById(targets.koraDeleteId);
    }

    @Benchmark
    public int myBatisPlusDeleteById(BenchmarkState state, DeleteTargetState targets) {
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            int result = session.getMapper(MyBatisPlusUserMapper.class).deleteById(targets.plusDeleteId);
            session.commit();
            return result;
        }
    }

    @Benchmark
    public int jooqDeleteById(BenchmarkState state, DeleteTargetState targets) {
        return state.jooq.deleteFrom(USERS)
                .where(USER_ID.eq(targets.jooqDeleteId))
                .execute();
    }

    @Benchmark
    public int jimmerDeleteById(BenchmarkState state, DeleteTargetState targets) {
        return state.jimmerSqlClient.deleteById(JimmerUser.class, targets.jimmerDeleteId)
                .getTotalAffectedRowCount();
    }

    @Benchmark
    public Page<User> koraPage(BenchmarkState state) {
        return state.koraMapper.selectPage(Paging.of(1, 20), state.minAge, state.maxAge);
    }

    @Benchmark
    public PlusPageResult myBatisPlusPage(BenchmarkState state) {
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            MyBatisPlusUserMapper mapper = session.getMapper(MyBatisPlusUserMapper.class);
            long total = mapper.countAgeRange(state.minAge, state.maxAge);
            List<PlusUser> rows = mapper.selectPageRows(state.minAge, state.maxAge, 20, 0);
            return new PlusPageResult(total, rows.size());
        }
    }

    @Benchmark
    public JooqPageResult jooqPage(BenchmarkState state) {
        long total = state.jooq.selectCount()
                .from(USERS)
                .where(USER_AGE.ge(state.minAge))
                .and(USER_AGE.le(state.maxAge))
                .fetchOne(0, long.class);
        List<JooqUser> rows = state.jooq.select(USER_ID, USER_NAME, USER_AGE, USER_LOGIN_NAME)
                .from(USERS)
                .where(USER_AGE.ge(state.minAge))
                .and(USER_AGE.le(state.maxAge))
                .orderBy(USER_ID.asc())
                .limit(20)
                .offset(0)
                .fetch(SimpleMapperPerformanceBenchmark::toJooqUser);
        return new JooqPageResult(total, rows.size());
    }

    @Benchmark
    public org.babyfish.jimmer.Page<JimmerUser> jimmerPage(BenchmarkState state) {
        JimmerUserTable table = JimmerUserTable.$;
        return state.jimmerSqlClient.createQuery(table)
                .where(table.age().ge(state.minAge))
                .where(table.age().le(state.maxAge))
                .orderBy(table.id())
                .select(table.fetch(JimmerUserFetcher.$.allScalarFields()))
                .fetchPage(0, 20);
    }

    @Benchmark
    public int koraBatchInsert(BenchmarkState state) {
        long base = state.koraBatchBase.getAndAdd(100);
        return state.koraMapper.insert(state.newKoraUsers(base, 100));
    }

    @Benchmark
    public int myBatisPlusBatchInsert(BenchmarkState state) {
        long base = state.plusBatchBase.getAndAdd(100);
        try (SqlSession session = state.myBatisPlusFactory.openSession(ExecutorType.BATCH, false)) {
            MyBatisPlusUserMapper mapper = session.getMapper(MyBatisPlusUserMapper.class);
            for (PlusUser user : state.newPlusUsers(base, 100)) {
                mapper.insert(user);
            }
            session.flushStatements();
            session.commit();
            return 100;
        }
    }

    @Benchmark
    public int jooqBatchInsert(BenchmarkState state) {
        long base = state.jooqBatchBase.getAndAdd(100);
        List<Query> queries = state.newJooqUsers(base, 100).stream()
                .map(user -> (Query) state.jooq.insertInto(USERS)
                        .columns(USER_ID, USER_NAME, USER_AGE, USER_LOGIN_NAME)
                        .values(user.id(), user.name(), user.age(), user.loginName()))
                .toList();
        return Arrays.stream(state.jooq.batch(queries).execute()).sum();
    }

    @Benchmark
    public int jimmerBatchInsert(BenchmarkState state) {
        long base = state.jimmerBatchBase.getAndAdd(100);
        return state.jimmerSqlClient.saveEntitiesCommand(state.newJimmerUsers(base, 100))
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getTotalAffectedRowCount();
    }

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private DataSource dataSource;
        private UserMapper koraMapper;
        private SqlSessionFactory myBatisFactory;
        private SqlSessionFactory myBatisPlusFactory;
        private DSLContext jooq;
        private JSqlClient jimmerSqlClient;
        private Long selectId;
        private Integer minAge;
        private Integer maxAge;
        private AtomicLong koraInsertId;
        private AtomicLong plusInsertId;
        private AtomicLong jooqInsertId;
        private AtomicLong jimmerInsertId;
        private AtomicLong koraDeleteId;
        private AtomicLong plusDeleteId;
        private AtomicLong jooqDeleteId;
        private AtomicLong jimmerDeleteId;
        private AtomicLong koraUpdateId;
        private AtomicLong plusUpdateId;
        private AtomicLong jooqUpdateId;
        private AtomicLong jimmerUpdateId;
        private AtomicLong koraBatchBase;
        private AtomicLong plusBatchBase;
        private AtomicLong jooqBatchBase;
        private AtomicLong jimmerBatchBase;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:kora-benchmark;MODE=MYSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");

            initializeSchema(dataSource);
            this.dataSource = dataSource;
            this.koraMapper = createKoraMapper(dataSource);
            this.myBatisFactory = createMyBatisFactory(dataSource);
            this.myBatisPlusFactory = createMyBatisPlusFactory(dataSource);
            this.jooq = createJooq(dataSource);
            this.jimmerSqlClient = createJimmerSqlClient(dataSource);
            this.selectId = 512L;
            this.minAge = 20;
            this.maxAge = 35;
            this.koraInsertId = new AtomicLong(1_000_000L);
            this.plusInsertId = new AtomicLong(2_000_000L);
            this.jooqInsertId = new AtomicLong(3_000_000L);
            this.jimmerInsertId = new AtomicLong(4_000_000L);
            this.koraDeleteId = new AtomicLong(100_001L);
            this.plusDeleteId = new AtomicLong(200_001L);
            this.jooqDeleteId = new AtomicLong(300_001L);
            this.jimmerDeleteId = new AtomicLong(400_001L);
            this.koraUpdateId = new AtomicLong(1L);
            this.plusUpdateId = new AtomicLong(1L);
            this.jooqUpdateId = new AtomicLong(1L);
            this.jimmerUpdateId = new AtomicLong(1L);
            this.koraBatchBase = new AtomicLong(5_000_000L);
            this.plusBatchBase = new AtomicLong(6_000_000L);
            this.jooqBatchBase = new AtomicLong(7_000_000L);
            this.jimmerBatchBase = new AtomicLong(8_000_000L);
        }

        private UserMapper createKoraMapper(DataSource dataSource) {
            DefaultSqlExecutor sqlExecutor = new DefaultSqlExecutor(dataSource);
            sqlExecutor.setDbType(DbType.H2);
            return new UserMapperImpl(sqlExecutor);
        }

        private SqlSessionFactory createMyBatisFactory(DataSource dataSource) {
            org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
            configuration.setEnvironment(new Environment("benchmark", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(MyBatisUserMapper.class);
            return new SqlSessionFactoryBuilder().build(configuration);
        }

        private SqlSessionFactory createMyBatisPlusFactory(DataSource dataSource) {
            MybatisConfiguration configuration = new MybatisConfiguration();
            configuration.setEnvironment(new Environment("benchmark-plus", new JdbcTransactionFactory(), dataSource));
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.addMapper(MyBatisPlusUserMapper.class);
            return new MybatisSqlSessionFactoryBuilder().build(configuration);
        }

        private DSLContext createJooq(DataSource dataSource) {
            System.setProperty("org.jooq.no-logo", "true");
            System.setProperty("org.jooq.no-tips", "true");
            return DSL.using(dataSource, SQLDialect.H2, new Settings().withExecuteLogging(false));
        }

        private JSqlClient createJimmerSqlClient(DataSource dataSource) {
            return JSqlClient.newBuilder()
                    .setConnectionManager(ConnectionManager.simpleConnectionManager(dataSource))
                    .setDialect(new H2Dialect())
                    .build();
        }

        private void initializeSchema(DataSource dataSource) throws Exception {
            try (Connection connection = dataSource.getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("drop table if exists users");
                statement.execute("""
                        create table users (
                            id bigint primary key,
                            name varchar(64),
                            age integer,
                            login_name varchar(64)
                        )
                        """);
            }
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "insert into users(id, name, age, login_name) values (?, ?, ?, ?)")) {
                for (long id = 1; id <= 1000; id++) {
                    statement.setLong(1, id);
                    statement.setString(2, "user-" + id);
                    statement.setInt(3, 18 + (int) (id % 40));
                    statement.setString(4, "login-" + id);
                    statement.addBatch();
                }
                statement.executeBatch();
            }
        }

        private long existingUpdateId(AtomicLong counter) {
            return 1 + Math.floorMod(counter.getAndIncrement(), 1_000L);
        }

        private void ensureUser(long id, String prefix) throws Exception {
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(
                         "merge into users(id, name, age, login_name) key(id) values (?, ?, ?, ?)")) {
                statement.setLong(1, id);
                statement.setString(2, prefix + "-user-" + id);
                statement.setInt(3, 20);
                statement.setString(4, prefix + "-login-" + id);
                statement.executeUpdate();
            }
        }

        private User newKoraUser(long id) {
            return new User(id, "user-" + id, 18 + (int) (id % 40), "login-" + id);
        }

        private List<User> newKoraUsers(long base, int size) {
            return java.util.stream.LongStream.range(base, base + size)
                    .mapToObj(this::newKoraUser)
                    .toList();
        }

        private PlusUser newPlusUser(long id) {
            PlusUser user = new PlusUser();
            user.setId(id);
            user.setName("user-" + id);
            user.setAge(18 + (int) (id % 40));
            user.setUserName("login-" + id);
            return user;
        }

        private List<PlusUser> newPlusUsers(long base, int size) {
            return java.util.stream.LongStream.range(base, base + size)
                    .mapToObj(this::newPlusUser)
                    .toList();
        }

        private JimmerUser newJimmerUser(long id) {
            return newJimmerUser(id, "user-" + id);
        }

        private JimmerUser newJimmerUser(long id, String name) {
            return JimmerUserDraft.$.produce(draft -> {
                draft.setId(id);
                draft.setName(name);
                draft.setAge(18 + (int) (id % 40));
                draft.setLoginName("login-" + id);
            });
        }

        private List<JimmerUser> newJimmerUsers(long base, int size) {
            return java.util.stream.LongStream.range(base, base + size)
                    .mapToObj(this::newJimmerUser)
                    .toList();
        }

        private List<JooqUser> newJooqUsers(long base, int size) {
            return java.util.stream.LongStream.range(base, base + size)
                    .mapToObj(id -> new JooqUser(id, "user-" + id, 18 + (int) (id % 40), "login-" + id))
                    .toList();
        }
    }

    @State(Scope.Thread)
    public static class UpdateTargetState {
        private long koraUpdateId;
        private long plusUpdateId;
        private long jooqUpdateId;
        private long jimmerUpdateId;

        @Setup(Level.Invocation)
        public void setup(BenchmarkState state) {
            this.koraUpdateId = state.existingUpdateId(state.koraUpdateId);
            this.plusUpdateId = state.existingUpdateId(state.plusUpdateId);
            this.jooqUpdateId = state.existingUpdateId(state.jooqUpdateId);
            this.jimmerUpdateId = state.existingUpdateId(state.jimmerUpdateId);
        }
    }

    @State(Scope.Thread)
    public static class DeleteTargetState {
        private long koraDeleteId;
        private long plusDeleteId;
        private long jooqDeleteId;
        private long jimmerDeleteId;

        @Setup(Level.Invocation)
        public void setup(BenchmarkState state) throws Exception {
            this.koraDeleteId = state.koraDeleteId.getAndIncrement();
            this.plusDeleteId = state.plusDeleteId.getAndIncrement();
            this.jooqDeleteId = state.jooqDeleteId.getAndIncrement();
            this.jimmerDeleteId = state.jimmerDeleteId.getAndIncrement();
            state.ensureUser(koraDeleteId, "kora-delete");
            state.ensureUser(plusDeleteId, "plus-delete");
            state.ensureUser(jooqDeleteId, "jooq-delete");
            state.ensureUser(jimmerDeleteId, "jimmer-delete");
        }
    }

    private static JooqUser toJooqUser(org.jooq.Record record) {
        return new JooqUser(
                record.get(USER_ID),
                record.get(USER_NAME),
                record.get(USER_AGE),
                record.get(USER_LOGIN_NAME)
        );
    }
}

interface MyBatisUserMapper {
    @Select("""
            select id, name, age, login_name
            from users
            where id = #{id}
            """)
    User selectById(@Param("id") Long id);

    @Select("""
            <script>
            select id, name, age, login_name
            from users
            where age &gt;= #{minAge}
              and age &lt;= #{maxAge}
            order by id
            </script>
            """)
    List<User> selectByAgeRange(@Param("minAge") Integer minAge, @Param("maxAge") Integer maxAge);
}

@TableName("users")
class PlusUser {
    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private String name;
    private Integer age;
    @TableField("login_name")
    private String userName;

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

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}

interface MyBatisPlusUserMapper extends BaseMapper<PlusUser> {
    @Select("""
            select count(*)
            from users
            where age >= #{minAge}
              and age <= #{maxAge}
            """)
    long countAgeRange(@Param("minAge") Integer minAge, @Param("maxAge") Integer maxAge);

    @Select("""
            <script>
            select id, name, age, login_name
            from users
            where age &gt;= #{minAge}
              and age &lt;= #{maxAge}
            order by id
            limit #{size} offset #{offset}
            </script>
            """)
    List<PlusUser> selectPageRows(@Param("minAge") Integer minAge,
                                  @Param("maxAge") Integer maxAge,
                                  @Param("size") Integer size,
                                  @Param("offset") Integer offset);
}

record PlusPageResult(long total, int size) {
}

record JooqUser(Long id, String name, Integer age, String loginName) {
}

record JooqPageResult(long total, int size) {
}
