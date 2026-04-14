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
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.h2.jdbcx.JdbcDataSource;
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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class SimpleMapperPerformanceBenchmark {
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
    public int koraUpdateById(BenchmarkState state) {
        long id = state.koraUpdateId.getAndIncrement();
        User user = state.newKoraUser(id);
        user.setName("updated-" + id);
        return state.koraMapper.updateById(user);
    }

    @Benchmark
    public int myBatisPlusUpdateById(BenchmarkState state) {
        long id = state.plusUpdateId.getAndIncrement();
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            PlusUser user = state.newPlusUser(id);
            user.setName("updated-" + id);
            int result = session.getMapper(MyBatisPlusUserMapper.class).updateById(user);
            session.commit();
            return result;
        }
    }

    @Benchmark
    public int koraDeleteById(BenchmarkState state) {
        return state.koraMapper.deleteById(state.koraDeleteId.getAndIncrement());
    }

    @Benchmark
    public int myBatisPlusDeleteById(BenchmarkState state) {
        try (SqlSession session = state.myBatisPlusFactory.openSession()) {
            int result = session.getMapper(MyBatisPlusUserMapper.class).deleteById(state.plusDeleteId.getAndIncrement());
            session.commit();
            return result;
        }
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

    @State(Scope.Benchmark)
    public static class BenchmarkState {
        private UserMapper koraMapper;
        private SqlSessionFactory myBatisFactory;
        private SqlSessionFactory myBatisPlusFactory;
        private Long selectId;
        private Integer minAge;
        private Integer maxAge;
        private AtomicLong koraInsertId;
        private AtomicLong plusInsertId;
        private AtomicLong koraDeleteId;
        private AtomicLong plusDeleteId;
        private AtomicLong koraUpdateId;
        private AtomicLong plusUpdateId;
        private AtomicLong koraBatchBase;
        private AtomicLong plusBatchBase;

        @Setup(Level.Trial)
        public void setup() throws Exception {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:kora-benchmark;MODE=MYSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
            dataSource.setUser("sa");
            dataSource.setPassword("");

            initializeSchema(dataSource);
            this.koraMapper = createKoraMapper(dataSource);
            this.myBatisFactory = createMyBatisFactory(dataSource);
            this.myBatisPlusFactory = createMyBatisPlusFactory(dataSource);
            this.selectId = 512L;
            this.minAge = 20;
            this.maxAge = 35;
            this.koraInsertId = new AtomicLong(1_000_000L);
            this.plusInsertId = new AtomicLong(2_000_000L);
            this.koraDeleteId = new AtomicLong(100_001L);
            this.plusDeleteId = new AtomicLong(200_001L);
            this.koraUpdateId = new AtomicLong(1L);
            this.plusUpdateId = new AtomicLong(1L);
            this.koraBatchBase = new AtomicLong(3_000_000L);
            this.plusBatchBase = new AtomicLong(4_000_000L);
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
                for (long id = 100_001; id <= 150_000; id++) {
                    statement.setLong(1, id);
                    statement.setString(2, "kora-delete-" + id);
                    statement.setInt(3, 20);
                    statement.setString(4, "kora-del-" + id);
                    statement.addBatch();
                }
                for (long id = 200_001; id <= 250_000; id++) {
                    statement.setLong(1, id);
                    statement.setString(2, "plus-delete-" + id);
                    statement.setInt(3, 20);
                    statement.setString(4, "plus-del-" + id);
                    statement.addBatch();
                }
                statement.executeBatch();
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
