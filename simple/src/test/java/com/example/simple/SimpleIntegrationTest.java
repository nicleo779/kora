package com.example.simple;

import com.example.simple.entity.User;
import com.example.simple.entity.meta.USER;
import com.example.simple.mapper.UserMapper;
import com.example.simple.mapper.UserMapperImpl;
import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.MethodInfo;
import com.nicleo.kora.core.runtime.SqlExecutionContext;
import com.nicleo.kora.core.runtime.SqlInterceptor;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleIntegrationTest {
    private JdbcDataSource dataSource;
    private SqlSession sqlSession;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:simple;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        this.sqlSession = new DefaultSqlSession(dataSource);
        this.userMapper = new UserMapperImpl(sqlSession);

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("drop table if exists users");
            statement.execute("create table users(id bigint auto_increment primary key, name varchar(64), age int)");
            statement.execute("insert into users(name, age) values ('Alice', 18), ('Bob', 25), ('Cindy', 30)");
        }
    }

    @Test
    void generatedMapperAndMetaShouldWork() {
        assertEquals("name", USER.name);
        assertEquals("age", USER.age);
        assertEquals("user_name",USER.userName);
        User alice = userMapper.selectById(1L);
        assertNotNull(alice);
        assertEquals("Alice", alice.getName());

        List<User> ranged = userMapper.selectByAgeRange(20, 30);
        assertEquals(2, ranged.size());

        List<User> ids = userMapper.selectByIds(List.of(1L, 3L));
        assertEquals(List.of("Alice", "Cindy"), ids.stream().map(User::getName).toList());

        int updated = userMapper.insert(new User(null, "Dylan", 27,"zhansan"));
        assertEquals(1, updated);
        assertEquals(4, userMapper.selectByAgeRange(null, null).size());
    }

    @Test
    void generatedMapperShouldPassExecutionContextToInterceptor() {
        List<SqlExecutionContext> contexts = new ArrayList<>();
        SqlInterceptor interceptor = (context, request) -> {
            contexts.add(context);
            return request;
        };
        this.sqlSession = new DefaultSqlSession(dataSource, interceptor);
        this.userMapper = new UserMapperImpl(sqlSession);

        User user = userMapper.selectById(1L);

        assertNotNull(user);
        assertEquals(1, contexts.size());
        assertEquals("com.example.simple.mapper.UserMapper", contexts.get(0).getMapperClassName());
        assertEquals("selectById", contexts.get(0).getMapperMethodName());
        assertEquals("selectById", contexts.get(0).getStatementId());
        assertEquals(User.class, contexts.get(0).getResultType());
    }

    @Test
    void generatedReflectorShouldExposeFieldAndMethodMetadata() {
        GeneratedReflector<User> reflector = GeneratedReflectors.get(User.class);

        FieldInfo nameField = reflector.getField("name");
        MethodInfo[] getNameMethods = reflector.getMethod("getName");

        assertNotNull(nameField);
        assertEquals("name", nameField.name());
        assertEquals(String.class, nameField.type());
        assertEquals("getName", nameField.getter());
        assertEquals("setName", nameField.setter());
        assertTrue(reflector.getFields().length >= 4);
        assertEquals(1, getNameMethods.length);
        assertEquals(String.class, getNameMethods[0].returnType());
        assertEquals(0, getNameMethods[0].params().length);
    }
}
