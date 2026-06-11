package org.byteora.kyra.orm;

import org.byteora.kyra.orm.dynamic.DynamicSqlArgumentResolver;
import org.byteora.kyra.orm.dynamic.DynamicSqlNode;
import org.byteora.kyra.orm.dynamic.DynamicSqlRenderer;
import org.byteora.kyra.orm.dynamic.MapperParameters;
import org.byteora.kyra.orm.dynamic.SqlNodes;
import org.byteora.kyra.orm.runtime.BoundSql;
import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.runtime.GeneratedReflectors;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DynamicSqlRendererTest {
    @BeforeAll
    static void installReflector() {
        ReflectorRegistry.clear();
        ReflectorRegistry.register(User.class, GeneratedReflectors.load(User.class));
    }

    @Test
    void rendersIfAndWhereNodes() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from user "),
                SqlNodes.trim("WHERE", null, new String[]{"AND", "OR"}, new String[0],
                        SqlNodes.mixed(
                                SqlNodes.ifNode("id != null", SqlNodes.text(" and id = #{id}")),
                                SqlNodes.ifNode("user.name != null and user.name != ''", SqlNodes.text(" and name = #{user.name}"))
                        ))
        );

        BoundSql boundSql = DynamicSqlRenderer.render(root, MapperParameters.build(
                new String[]{"id", "user"},
                new Object[]{1L, new User("Neo", 18)}
        ));

        assertEquals("select * from user WHERE id = ? and name = ?", boundSql.getSql());
        assertArrayEquals(new Object[]{1L, "Neo"}, DynamicSqlArgumentResolver.resolve(boundSql));
    }

    @Test
    void ifExpressionShouldConsumeRightSideWhenLeftSideIsFalse() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from user "),
                SqlNodes.trim("WHERE", null, new String[]{"AND", "OR"}, new String[0],
                        SqlNodes.ifNode("user.name != null and user.name != ''", SqlNodes.text(" and name = #{user.name}")))
        );

        BoundSql boundSql = DynamicSqlRenderer.render(root, MapperParameters.build(
                new String[]{"user"},
                new Object[]{new User(null, 18)}
        ));

        assertEquals("select * from user", boundSql.getSql());
        assertArrayEquals(new Object[0], DynamicSqlArgumentResolver.resolve(boundSql));
    }

    @Test
    void ifExpressionShouldSupportSizeAndIsEmptyMethods() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from user "),
                SqlNodes.trim("WHERE", null, new String[]{"AND", "OR"}, new String[0],
                        SqlNodes.mixed(
                                SqlNodes.ifNode("ids != null and ids.size() > 0", SqlNodes.text(" and 2 = 2")),
                                SqlNodes.ifNode("emptyIds != null and emptyIds.isEmpty()", SqlNodes.text(" and 1 = 1"))
                        ))
        );

        BoundSql boundSql = DynamicSqlRenderer.render(root, Map.of(
                "ids", List.of(7),
                "emptyIds", List.of(),
                "_parameter", Map.of("ids", List.of(7), "emptyIds", List.of())
        ));

        assertEquals("select * from user WHERE 2 = 2 and 1 = 1", boundSql.getSql());
        assertArrayEquals(new Object[0], DynamicSqlArgumentResolver.resolve(boundSql));
    }

    @Test
    void ifExpressionShouldSupportLengthAndContainsMethods() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from user "),
                SqlNodes.trim("WHERE", null, new String[]{"AND", "OR"}, new String[0],
                        SqlNodes.mixed(
                                SqlNodes.ifNode("name != null and name.length() > 0", SqlNodes.text(" and 3 = 3")),
                                SqlNodes.ifNode("ids.contains(7)", SqlNodes.text(" and 4 = 4")),
                                SqlNodes.ifNode("name.contains('eo')", SqlNodes.text(" and 5 = 5"))
                        ))
        );

        BoundSql boundSql = DynamicSqlRenderer.render(root, Map.of(
                "name", "Neo",
                "ids", List.of(7, 8),
                "_parameter", Map.of("name", "Neo", "ids", List.of(7, 8))
        ));

        assertEquals("select * from user WHERE 3 = 3 and 4 = 4 and 5 = 5", boundSql.getSql());
        assertArrayEquals(new Object[0], DynamicSqlArgumentResolver.resolve(boundSql));
    }

    @Test
    void ifExpressionShouldSupportEnumOrdinalMethod() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from user "),
                SqlNodes.trim("WHERE", null, new String[]{"AND", "OR"}, new String[0],
                        SqlNodes.ifNode("status != null and status.ordinal() == 1", SqlNodes.text(" and 6 = 6")))
        );

        BoundSql boundSql = DynamicSqlRenderer.render(root, Map.of(
                "status", UserStatus.ACTIVE,
                "_parameter", Map.of("status", UserStatus.ACTIVE)
        ));

        assertEquals("select * from user WHERE 6 = 6", boundSql.getSql());
        assertArrayEquals(new Object[0], DynamicSqlArgumentResolver.resolve(boundSql));
    }

    @Test
    void ifExpressionShouldSupportEnumNameMethod() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from user "),
                SqlNodes.trim("WHERE", null, new String[]{"AND", "OR"}, new String[0],
                        SqlNodes.ifNode("status != null and status.name() == 'ACTIVE'", SqlNodes.text(" and 7 = 7")))
        );

        BoundSql boundSql = DynamicSqlRenderer.render(root, Map.of(
                "status", UserStatus.ACTIVE,
                "_parameter", Map.of("status", UserStatus.ACTIVE)
        ));

        assertEquals("select * from user WHERE 7 = 7", boundSql.getSql());
        assertArrayEquals(new Object[0], DynamicSqlArgumentResolver.resolve(boundSql));
    }

    @Test
    void rendersForeachChooseAndBind() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.bind("pattern", "'%' + name + '%'"),
                SqlNodes.text("select * from user "),
                SqlNodes.choose(
                        new org.byteora.kyra.orm.dynamic.WhenSqlNode[]{
                                SqlNodes.when("ids != null", SqlNodes.mixed(
                                        SqlNodes.text("where id in "),
                                        SqlNodes.foreach("ids", "item", "idx", "(", ")", ", ", SqlNodes.text("#{item}"))
                                ))
                        },
                        SqlNodes.text("where name like #{pattern}")
                )
        );

        BoundSql listSql = DynamicSqlRenderer.render(root, Map.of("ids", List.of(1, 2, 3), "_parameter", Map.of("ids", List.of(1, 2, 3))));
        assertEquals("select * from user where id in (?, ?, ?)", listSql.getSql());
        assertArrayEquals(new Object[]{1, 2, 3}, DynamicSqlArgumentResolver.resolve(listSql));

        BoundSql bindSql = DynamicSqlRenderer.render(root, MapperParameters.build(new String[]{"name"}, new Object[]{"Keanu"}));
        assertEquals("select * from user where name like ?", bindSql.getSql());
        assertArrayEquals(new Object[]{"%Keanu%"}, DynamicSqlArgumentResolver.resolve(bindSql));
    }

    @Test
    void staticStatementShouldResolveFreshValuesPerCall() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from user where id = #{id}")
        );

        BoundSql first = DynamicSqlRenderer.render(root, MapperParameters.build(new String[]{"id"}, new Object[]{1L}));
        BoundSql second = DynamicSqlRenderer.render(root, MapperParameters.build(new String[]{"id"}, new Object[]{2L}));

        assertEquals("select * from user where id = ?", first.getSql());
        assertEquals("select * from user where id = ?", second.getSql());
        assertArrayEquals(new Object[]{1L}, DynamicSqlArgumentResolver.resolve(first));
        assertArrayEquals(new Object[]{2L}, DynamicSqlArgumentResolver.resolve(second));
    }

    @Test
    void dollarPlaceholderShouldStillBeSubstitutedInline() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.text("select * from ${table} where id = #{id}")
        );

        BoundSql boundSql = DynamicSqlRenderer.render(root, MapperParameters.build(
                new String[]{"table", "id"},
                new Object[]{"user", 9L}
        ));

        assertEquals("select * from user where id = ?", boundSql.getSql());
        assertArrayEquals(new Object[]{9L}, DynamicSqlArgumentResolver.resolve(boundSql));
    }

    @Reflect
    public static final class User {
        private final String name;
        private final int age;

        public User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }
    }

    enum UserStatus {
        INACTIVE,
        ACTIVE
    }
}
