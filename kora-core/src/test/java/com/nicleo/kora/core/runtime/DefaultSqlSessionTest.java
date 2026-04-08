package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.runtime.jdbc.DefaultSqlSession;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultSqlSessionTest {
    private DefaultSqlSession sqlSession;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:kora;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        this.sqlSession = new DefaultSqlSession(dataSource);
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
    void selectListReturnsAllRows() {
        List<UserAccount> users = sqlSession.selectList(
                "select id, user_name, age from user_account order by id",
                new Object[0],
                UserAccount.class
        );

        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).getUserName());
        assertEquals("Bob", users.get(1).getUserName());
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

    static final class TestReflectorResolver implements GeneratedReflectors.Resolver {
        @Override
        public <T> GeneratedReflector<T> get(Class<T> type) {
            if (type == UserAccount.class) {
                return (GeneratedReflector<T>) new UserAccountGeneratedReflector();
            }
            throw new SqlSessionException("No test reflector for type: " + type.getName());
        }
    }

    static final class UserAccount {
        private long id;
        private String userName;
        private int age;

        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getUserName() { return userName; }
        public void setUserName(String userName) { this.userName = userName; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }

    static final class UserAccountGeneratedReflector implements GeneratedReflector<UserAccount> {
        @Override
        public UserAccount newInstance() { return new UserAccount(); }
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
            switch (TypeConverter.normalize(property)) {
                case "id" -> target.setId((Long) TypeConverter.cast(value, Long.class));
                case "username" -> target.setUserName((String) TypeConverter.cast(value, String.class));
                case "age" -> target.setAge((Integer) TypeConverter.cast(value, Integer.class));
                default -> { }
            }
        }
        @Override
        public Object get(UserAccount target, String property) {
            return switch (TypeConverter.normalize(property)) {
                case "id" -> target.getId();
                case "username" -> target.getUserName();
                case "age" -> target.getAge();
                default -> throw new IllegalArgumentException("Unknown property: " + property);
            };
        }
    }
}
