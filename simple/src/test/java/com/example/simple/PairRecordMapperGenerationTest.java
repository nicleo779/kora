package com.example.simple;

import com.example.simple.common.Pair;
import com.example.simple.mapper.PairUserMapper;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlExecutor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PairRecordMapperGenerationTest {
    @Test
    void generatedMapperShouldAlsoGenerateRecordReflector() throws ClassNotFoundException {
        assertNotNull(Class.forName("com.example.simple.mapper.PairUserMapperImpl"));
        assertNotNull(Class.forName(GeneratedTypeNames.reflectorTypeName(Pair.class)));
    }

    @Test
    void mapperShouldPreserveNestedListElementGenericTypes() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:pair-generic-result;MODE=MYSQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=KEY,VALUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        initializeUsers(dataSource);

        DefaultSqlExecutor sqlExecutor = new DefaultSqlExecutor(dataSource);
        sqlExecutor.setDbType(DbType.H2);
        PairUserMapper mapper = newPairUserMapper(sqlExecutor);

        List<Pair<String, Long>> results = mapper.memberNumOfDay(
                LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDateTime.of(2026, 4, 2, 0, 0)
        );

        assertEquals(2, results.size());
        assertEquals("alice", results.get(0).key());
        assertEquals(2L, results.get(0).value());
        assertInstanceOf(String.class, results.get(0).key());
        assertInstanceOf(Long.class, results.get(0).value());
    }

    private PairUserMapper newPairUserMapper(SqlExecutor sqlExecutor) throws Exception {
        return (PairUserMapper) Class.forName("com.example.simple.mapper.PairUserMapperImpl")
                .getConstructor(SqlExecutor.class)
                .newInstance(sqlExecutor);
    }

    private void initializeUsers(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table users (
                        id bigint primary key,
                        name varchar(64),
                        created_time timestamp
                    )
                    """);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert into users(id, name, created_time) values (?, ?, ?)")) {
            insertUser(statement, 1L, "alice", LocalDateTime.of(2026, 4, 1, 10, 0));
            insertUser(statement, 2L, "alice", LocalDateTime.of(2026, 4, 1, 11, 0));
            insertUser(statement, 3L, "bob", LocalDateTime.of(2026, 4, 1, 12, 0));
            statement.executeBatch();
        }
    }

    private void insertUser(PreparedStatement statement, long id, String name, LocalDateTime createdTime) throws Exception {
        statement.setLong(1, id);
        statement.setString(2, name);
        statement.setObject(3, createdTime);
        statement.addBatch();
    }
}
