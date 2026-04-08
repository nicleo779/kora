package com.example.simple;

import com.example.simple.entity.User;
import com.example.simple.entity.meta.USER;
import com.example.simple.mapper.UserMapper;
import com.example.simple.mapper.UserMapperImpl;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SimpleIntegrationTest {
    private SqlSession sqlSession;
    private UserMapper userMapper;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
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
}
