package com.example.simple;

import com.example.simple.dto.AgeRange;
import com.example.simple.dto.UserFilter;
import com.example.simple.dto.UserQuery;
import com.example.simple.dto.UserSummary;
import com.example.simple.entity.User;
import com.example.simple.common.ReadMapper;
import com.example.simple.mapper.UserMapper;
import com.example.simple.mapper.UserMapperImpl;
import com.example.simple.query.UserTable;
import com.nicleo.kora.core.mapper.BaseMapper;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.query.Wrapper;
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
            statement.execute("create table users(id bigint auto_increment primary key, name varchar(64), age int, login_name varchar(64))");
            statement.execute("insert into users(name, age, login_name) values ('Alice', 18, 'alice_01'), ('Bob', 25, 'bob_02'), ('Cindy', 30, 'cindy_03')");
        }
    }

    @Test
    void generatedMapperAndMetaShouldWork() {
        assertEquals("users", UserTable.USERS.tableName());
        assertEquals("id", UserTable.USERS.ID.columnName());
        assertEquals("name", UserTable.USERS.NAME.columnName());
        assertEquals("age", UserTable.USERS.AGE.columnName());
        assertEquals("login_name", UserTable.USERS.USER_NAME.columnName());
        User alice = userMapper.selectById(1L);
        assertNotNull(alice);
        assertEquals(1L, alice.getId());
        assertEquals("Alice", alice.getName());
        assertEquals("alice_01", alice.getUserName());

        List<User> ranged = userMapper.selectByAgeRange(20, 30);
        assertEquals(2, ranged.size());

        List<Long> queryIds = List.of(1L, 3L);
        List<User> ids = userMapper.selectByIds(queryIds);
        assertEquals(List.of("Alice", "Cindy"), ids.stream().map(User::getName).toList());

        List<UserSummary> summaries = userMapper.selectSummaries(new UserQuery(20, 30));
        assertEquals(2, summaries.size());
        assertEquals("Bob", summaries.get(0).getName());
        assertEquals("bob_02", summaries.get(0).getUserName());

        List<User> nested = userMapper.selectByNestedFilter(new UserFilter(new AgeRange(20, 30)));
        assertEquals(List.of("Bob", "Cindy"), nested.stream().map(User::getName).toList());

        assertEquals(1, userMapper.updateNameById(1L, "Alicia"));
        assertEquals("Alicia", userMapper.selectById(1L).getName());

        Integer minAge = 20;
        Integer maxAge = 30;
        List<User> wrapped = userMapper.selectList(
                Wrapper.<User>where()
                        .where(where -> where
                                .ge(minAge != null, UserTable.USERS.AGE, minAge)
                                .le(maxAge != null, UserTable.USERS.AGE, maxAge))
                        .orderBy(order -> order.asc(UserTable.USERS.ID))
        );
        assertEquals(List.of("Bob", "Cindy"), wrapped.stream().map(User::getName).toList());

        User wrappedOne = userMapper.selectOne(
                Wrapper.<User>where()
                        .where(where -> where.eq(UserTable.USERS.ID, 2L))
                        .limit(1)
        );
        assertNotNull(wrappedOne);
        assertEquals("Bob", wrappedOne.getName());

        BaseMapper<User> baseMapper = (ReadMapper<User>) userMapper;

        int updated = baseMapper.insert(new User(null, "Dylan", 27,"zhansan"));
        assertEquals(1, updated);
        assertEquals(4, userMapper.selectByAgeRange(null, null).size());
        assertEquals("zhansan", userMapper.selectById(4L).getUserName());
        User selectedByCapability = baseMapper.selectById(1L);
        assertNotNull(selectedByCapability);
        assertEquals("Alicia", selectedByCapability.getName());

        List<java.io.Serializable> capabilityIds = List.of(1L, 3L);
        List<User> selectedByIds = baseMapper.selectByIds(capabilityIds);
        assertEquals(List.of("Alicia", "Cindy"), selectedByIds.stream().map(User::getName).toList());

        Paging paging = new Paging();
        paging.setIndex(1L);
        paging.setSize(2L);
        Page<User> page = baseMapper.page(
                paging,
                Wrapper.<User>where()
                        .orderBy(order -> order.asc(UserTable.USERS.ID))
        );
        assertEquals(1L, page.current());
        assertEquals(4L, page.total());
        assertEquals(List.of("Alicia", "Bob"), page.records().stream().map(User::getName).toList());

        Paging xmlPaging = new Paging();
        xmlPaging.setIndex(2L);
        xmlPaging.setSize(2L);
        Page<User> xmlPage = userMapper.selectPage(xmlPaging, 18, 30);
        assertEquals(2L, xmlPage.current());
        assertEquals(4L, xmlPage.total());
        assertEquals(List.of("Cindy", "Dylan"), xmlPage.records().stream().map(User::getName).toList());

        int insertedByBase = baseMapper.insert(new User(null, "Ethan", 31, "ethan_05"));
        assertEquals(1, insertedByBase);
        assertEquals("Ethan", userMapper.selectById(5L).getName());

        User updateTarget = new User(5L, "EthanX", null, null);
        assertEquals(1, baseMapper.updateById(updateTarget));
        assertEquals("EthanX", userMapper.selectById(5L).getName());

        assertEquals(1, baseMapper.update(
                Wrapper.<User>update()
                        .set(UserTable.USERS.NAME, "Bobby")
                        .where(where -> where.eq(UserTable.USERS.ID, 2L))
                        .limit(1)
        ));
        assertEquals("Bobby", userMapper.selectById(2L).getName());

        assertEquals(1, baseMapper.deleteById(5L));
        assertEquals(4, userMapper.selectByAgeRange(null, null).size());

        assertEquals(2, baseMapper.insert(List.of(
                new User(null, "Fiona", 26, "fiona_06"),
                new User(null, "Gina", 28, "gina_07")
        )));
        assertEquals("Fiona", userMapper.selectById(6L).getName());
        assertEquals("Gina", userMapper.selectById(7L).getName());

        assertEquals(2, baseMapper.updateById(List.of(
                new User(6L, "FionaX", null, null),
                new User(7L, "GinaX", null, null)
        )));
        assertEquals("FionaX", userMapper.selectById(6L).getName());
        assertEquals("GinaX", userMapper.selectById(7L).getName());

        assertEquals(2, baseMapper.delete(
                Wrapper.<User>where()
                        .where(where -> where.in(UserTable.USERS.ID, List.of(6L, 7L)))
        ));
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
        FieldInfo idField = reflector.getField("id");
        MethodInfo[] getNameMethods = reflector.getMethod("getName");

        assertNotNull(nameField);
        assertNotNull(idField);
        assertEquals("name", nameField.name());
        assertEquals(String.class, nameField.type());
        assertEquals("id", idField.name());
        assertEquals(Long.class, idField.type());
        FieldInfo idsField = reflector.getField("ids");
        assertNotNull(idsField);
        assertTrue(idsField.type() instanceof ParameterizedType);
        ParameterizedType idsFieldType = (ParameterizedType) idsField.type();
        assertEquals(List.class, idsFieldType.getRawType());
        assertEquals(String.class, idsFieldType.getActualTypeArguments()[0]);
        assertTrue(Arrays.asList(reflector.getFields()).contains("name"));
        assertTrue(Arrays.asList(reflector.getFields()).contains("id"));
        assertEquals(0, reflector.getMethods().length);
        assertEquals(0, getNameMethods.length);
        MethodInfo[] getIdsMethods = reflector.getMethod("getIds");
        assertEquals(0, getIdsMethods.length);
        MethodInfo[] setIdsMethods = reflector.getMethod("setIds");
        assertEquals(0, setIdsMethods.length);
        Type superType = reflector.getClassInfo().superType();
        assertTrue(superType instanceof ParameterizedType);
        ParameterizedType parameterizedSuperType = (ParameterizedType) superType;
        assertEquals(com.example.simple.entity.BaseUser.class, parameterizedSuperType.getRawType());
        assertEquals(Long.class, parameterizedSuperType.getActualTypeArguments()[0]);
    }

    @Test
    void mapperParameterAndReturnTypesShouldAutoGenerateReflector() {
        GeneratedReflector<AgeRange> rangeReflector = GeneratedReflectors.get(AgeRange.class);
        GeneratedReflector<UserFilter> filterReflector = GeneratedReflectors.get(UserFilter.class);
        GeneratedReflector<UserQuery> queryReflector = GeneratedReflectors.get(UserQuery.class);
        GeneratedReflector<UserSummary> summaryReflector = GeneratedReflectors.get(UserSummary.class);

        AgeRange range = new AgeRange(18, 25);
        UserFilter filter = new UserFilter(range);
        UserQuery query = new UserQuery(18, 25);
        assertEquals(18, rangeReflector.get(range, "minAge"));
        assertSame(range, filterReflector.get(filter, "range"));
        assertEquals(18, queryReflector.get(query, "minAge"));
        assertEquals(25, queryReflector.get(query, "maxAge"));
        assertNotNull(summaryReflector.getField("userName"));
        assertTrue(summaryReflector.hasField("name"));
        assertThrows(UnsupportedOperationException.class, rangeReflector::newInstance);
        assertThrows(UnsupportedOperationException.class, filterReflector::newInstance);
        assertThrows(UnsupportedOperationException.class, queryReflector::newInstance);
        UserSummary summary = summaryReflector.newInstance();
        summaryReflector.set(summary, "name", "Demo");
        assertEquals("Demo", summary.getName());
    }
}
