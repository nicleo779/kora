package com.nicleo.kora.core;

import com.nicleo.kora.core.dynamic.DynamicSqlArgumentResolver;
import com.nicleo.kora.core.dynamic.DynamicSqlNode;
import com.nicleo.kora.core.dynamic.DynamicSqlRenderer;
import com.nicleo.kora.core.dynamic.MapperParameters;
import com.nicleo.kora.core.dynamic.SqlNodes;
import com.nicleo.kora.core.runtime.BoundSql;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamicSqlRendererTest {
    @BeforeAll
    static void installReflector() {
        GeneratedReflectors.install(new GeneratedReflectors.Resolver() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> GeneratedReflector<T> get(Class<T> type) {
                if (type == User.class) {
                    return (GeneratedReflector<T>) new UserReflector();
                }
                throw new IllegalArgumentException("unknown type: " + type);
            }
        });
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
    void rendersForeachChooseAndBind() {
        DynamicSqlNode root = SqlNodes.mixed(
                SqlNodes.bind("pattern", "'%' + name + '%'"),
                SqlNodes.text("select * from user "),
                SqlNodes.choose(
                        new com.nicleo.kora.core.dynamic.WhenSqlNode[]{
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

    static final class User {
        private final String name;
        private final int age;

        User(String name, int age) {
            this.name = name;
            this.age = age;
        }

        String getName() {
            return name;
        }

        int getAge() {
            return age;
        }
    }

    static final class UserReflector implements GeneratedReflector<User> {
        @Override
        public User newInstance() {
            return new User(null, 0);
        }

        @Override
        public Object invoke(User target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(User target, String property, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Object get(User target, String property) {
            return switch (property) {
                case "name" -> target.getName();
                case "age" -> target.getAge();
                default -> throw new IllegalArgumentException(property);
            };
        }
    }
}
