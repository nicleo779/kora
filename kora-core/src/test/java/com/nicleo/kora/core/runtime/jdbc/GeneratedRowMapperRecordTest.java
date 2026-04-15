package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.ClassInfo;
import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.ParameterInfo;
import com.nicleo.kora.core.runtime.TypeConverter;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedRowMapperRecordTest {
    @Test
    void shouldInstantiateRecordWithoutSetterAccess() throws Exception {
        GeneratedRowMapper<PairRecord> rowMapper = new GeneratedRowMapper<>(PairRecord.class, new PairRecordReflector(), new TypeConverter());

        PairRecord pair = rowMapper.mapRow(resultSet(new String[]{"key", "value"}, new Object[]{"day1", 12L}));

        assertEquals("day1", pair.key());
        assertEquals(12L, pair.value());
    }

    @Test
    void shouldInstantiatePojoWithConstructorAndSetterMix() throws Exception {
        GeneratedRowMapper<HybridUser> rowMapper = new GeneratedRowMapper<>(HybridUser.class, new HybridUserReflector(), new TypeConverter());

        HybridUser user = rowMapper.mapRow(resultSet(new String[]{"id", "age", "display_name"}, new Object[]{"u1", 18, "neo"}));

        assertEquals("u1", user.getId());
        assertEquals(18, user.getAge());
        assertEquals("neo", user.getDisplayName());
    }

    private ResultSet resultSet(String[] columns, Object[] values) {
        ResultSetMetaData metaData = (ResultSetMetaData) Proxy.newProxyInstance(
                ResultSetMetaData.class.getClassLoader(),
                new Class[]{ResultSetMetaData.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getColumnCount" -> columns.length;
                    case "getColumnLabel" -> columns[(Integer) args[0] - 1];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getMetaData" -> metaData;
                    case "getObject" -> values[(Integer) args[0] - 1];
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private record PairRecord(String key, Long value) {
    }

    private static final class HybridUser {
        private final String id;
        private Integer age;
        private String displayName;

        private HybridUser(String id) {
            this.id = id;
        }

        String getId() {
            return id;
        }

        Integer getAge() {
            return age;
        }

        void setAge(Integer age) {
            this.age = age;
        }

        String getDisplayName() {
            return displayName;
        }

        void setDisplayName(String displayName) {
            this.displayName = displayName;
        }
    }

    private static final class PairRecordReflector implements GeneratedReflector<PairRecord> {
        private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
        private static final ParameterInfo[] PARAMS = new ParameterInfo[]{
                new ParameterInfo("key", String.class, NO_ANNOTATIONS),
                new ParameterInfo("value", Long.class, NO_ANNOTATIONS)
        };

        @Override
        public PairRecord newInstance() {
            throw new AssertionError("record mapping should not use newInstance");
        }

        @Override
        public PairRecord newInstance(Object[] args) {
            return new PairRecord((String) args[0], (Long) args[1]);
        }

        @Override
        public ClassInfo getClassInfo() {
            return new ClassInfo(PairRecord.class, null, 0, NO_ANNOTATIONS, PARAMS);
        }

        @Override
        public Object invoke(PairRecord target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(PairRecord target, String property, Object value) {
            throw new AssertionError("record mapping should not use set");
        }

        @Override
        public Object get(PairRecord target, String property) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] fieldNamesView() {
            return new String[]{"key", "value"};
        }

        @Override
        public FieldInfo getField(String field) {
            return switch (field) {
                case "key" -> new FieldInfo("key", String.class, 0, null, NO_ANNOTATIONS);
                case "value" -> new FieldInfo("value", Long.class, 0, null, NO_ANNOTATIONS);
                default -> null;
            };
        }
    }

    private static final class HybridUserReflector implements GeneratedReflector<HybridUser> {
        private static final Annotation[] NO_ANNOTATIONS = new Annotation[0];
        private static final ParameterInfo[] PARAMS = new ParameterInfo[]{
                new ParameterInfo("id", String.class, NO_ANNOTATIONS)
        };

        @Override
        public HybridUser newInstance() {
            throw new AssertionError("hybrid mapping should use constructor metadata");
        }

        @Override
        public HybridUser newInstance(Object[] args) {
            return new HybridUser((String) args[0]);
        }

        @Override
        public ClassInfo getClassInfo() {
            return new ClassInfo(HybridUser.class, null, 0, NO_ANNOTATIONS, PARAMS);
        }

        @Override
        public Object invoke(HybridUser target, String method, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(HybridUser target, String property, Object value) {
            switch (property) {
                case "id" -> throw new AssertionError("constructor-bound property should not be set again");
                case "age" -> target.setAge((Integer) value);
                case "displayName" -> target.setDisplayName((String) value);
                default -> throw new UnsupportedOperationException(property);
            }
        }

        @Override
        public Object get(HybridUser target, String property) {
            return switch (property) {
                case "id" -> target.getId();
                case "age" -> target.getAge();
                case "displayName" -> target.getDisplayName();
                default -> throw new UnsupportedOperationException(property);
            };
        }

        @Override
        public String[] fieldNamesView() {
            return new String[]{"id", "age", "displayName"};
        }

        @Override
        public FieldInfo getField(String field) {
            return switch (field) {
                case "id" -> new FieldInfo("id", String.class, 0, null, NO_ANNOTATIONS);
                case "age" -> new FieldInfo("age", Integer.class, 0, null, NO_ANNOTATIONS);
                case "displayName" -> new FieldInfo("displayName", String.class, 0, "display_name", NO_ANNOTATIONS);
                default -> null;
            };
        }
    }
}
