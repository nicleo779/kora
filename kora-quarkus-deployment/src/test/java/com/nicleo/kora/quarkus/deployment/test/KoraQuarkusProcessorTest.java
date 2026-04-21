package com.nicleo.kora.quarkus.deployment.test;

import com.nicleo.kora.core.query.Tables;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.quarkus.Sql;
import io.quarkus.test.QuarkusUnitTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KoraQuarkusProcessorTest {
    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(
                            TestMapperKoraConfig.class,
                            TestUser.class,
                            TestUserMapper.class,
                            KoraQuarkusProcessorTest.class
                    )
                    .addAsResource("mapper/TestUserMapper.xml", "mapper/TestUserMapper.xml")
                    .addAsResource("application.properties", "application.properties"));

    @Inject
    TestUserMapper mapper;

    @Inject
    SqlExecutor sqlExecutor;

    @Test
    void shouldRegisterMapperBeansAndBindStaticSql() {
        assertNotNull(Tables.get(TestUser.class));

        sqlExecutor.update("drop table if exists test_user", new Object[0]);
        sqlExecutor.update("create table test_user(id bigint primary key, name varchar(32))", new Object[0]);
        sqlExecutor.update("insert into test_user(id, name) values(?, ?)", new Object[]{1L, "quarkus-user"});

        TestUser fromMapper = mapper.selectById(1L);
        TestUser fromSql = Sql.from(Tables.get(TestUser.class))
                .limit(1)
                .one(TestUser.class);

        assertNotNull(fromMapper);
        assertEquals("quarkus-user", fromMapper.getName());
        assertNotNull(fromSql);
        assertEquals("quarkus-user", fromSql.getName());
    }
}
