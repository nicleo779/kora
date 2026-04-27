package com.nicleo.kora.core.runtime.jdbc;

import com.nicleo.kora.core.runtime.*;
import org.junit.jupiter.api.Test;

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

    @Test
    void shouldMapSnakeCaseColumnToCamelCaseFieldWithoutAlias() throws Exception {
        GeneratedRowMapper<PlainUser> rowMapper = new GeneratedRowMapper<>(PlainUser.class, new PlainUserReflector(), new TypeConverter());

        PlainUser user = rowMapper.mapRow(resultSet(new String[]{"user_id", "first_name"}, new Object[]{"42", "ada"}));

        assertEquals("42", user.getUserId());
        assertEquals("ada", user.getFirstName());
    }

    @Test
    void shouldMapUppercasedColumnLabelsCaseInsensitively() throws Exception {
        GeneratedRowMapper<PlainUser> rowMapper = new GeneratedRowMapper<>(PlainUser.class, new PlainUserReflector(), new TypeConverter());

        PlainUser user = rowMapper.mapRow(resultSet(new String[]{"USER_ID", "FIRST_NAME"}, new Object[]{"7", "grace"}));

        assertEquals("7", user.getUserId());
        assertEquals("grace", user.getFirstName());
    }

    @Test
    void shouldReuseColumnPlanAcrossRows() throws Exception {
        GeneratedRowMapper<PlainUser> rowMapper = new GeneratedRowMapper<>(PlainUser.class, new PlainUserReflector(), new TypeConverter());
        int[] metadataCalls = new int[1];

        ResultSet rs = recordingResultSet(new String[]{"user_id", "first_name"}, new Object[]{"1", "alpha"}, metadataCalls);
        PlainUser row1 = rowMapper.mapRow(rs);
        PlainUser row2 = rowMapper.mapRow(rs);

        assertEquals("1", row1.getUserId());
        assertEquals("alpha", row1.getFirstName());
        assertEquals("1", row2.getUserId());
        assertEquals(1, metadataCalls[0], "column plan should be cached after the first row");
    }

    private ResultSet resultSet(String[] columns, Object[] values) {
        return recordingResultSet(columns, values, new int[1]);
    }

    private ResultSet recordingResultSet(String[] columns, Object[] values, int[] metadataCalls) {
        boolean[] lastWasNull = new boolean[1];
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
                    case "getMetaData" -> {
                        metadataCalls[0]++;
                        yield metaData;
                    }
                    case "getObject" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value;
                    }
                    case "getString" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value == null ? null : String.valueOf(value);
                    }
                    case "getInt" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value == null ? 0 : ((Number) value).intValue();
                    }
                    case "getLong" -> {
                        Object value = values[(Integer) args[0] - 1];
                        lastWasNull[0] = value == null;
                        yield value == null ? 0L : ((Number) value).longValue();
                    }
                    case "wasNull" -> lastWasNull[0];
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
        private static final AnnotationMeta[] NO_ANNOTATIONS = new AnnotationMeta[0];
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
        public Object invoke(PairRecord target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(PairRecord target, int propertyIndex, Object value) {
            throw new AssertionError("record mapping should not use indexed set");
        }

        @Override
        public Object get(PairRecord target, int index) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String[] getFields() {
            return new String[]{"key", "value"};
        }

        @Override
        public FieldInfo getField(int index) {
            return switch (index) {
                case 0 -> new FieldInfo("key", String.class, 0, null, NO_ANNOTATIONS);
                case 1 -> new FieldInfo("value", Long.class, 0, null, NO_ANNOTATIONS);
                default -> null;
            };
        }

        @Override
        public String[] getMethods() {
            return new String[0];
        }

        @Override
        public int getMethod(int index) {
            return -1;
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }

    private static final class HybridUserReflector implements GeneratedReflector<HybridUser> {
        private static final AnnotationMeta[] NO_ANNOTATIONS = new AnnotationMeta[0];
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
        public Object invoke(HybridUser target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(HybridUser target, int propertyIndex, Object value) {
            switch (propertyIndex) {
                case 0 -> throw new AssertionError("constructor-bound property should not be set again");
                case 1 -> target.setAge((Integer) value);
                case 2 -> target.setDisplayName((String) value);
                default -> throw new UnsupportedOperationException(String.valueOf(propertyIndex));
            }
        }

        @Override
        public Object get(HybridUser target, int index) {
            return switch (index) {
                case 0 -> target.getId();
                case 1 -> target.getAge();
                case 2 -> target.getDisplayName();
                default -> throw new UnsupportedOperationException(String.valueOf(index));
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"id", "age", "displayName"};
        }

        @Override
        public FieldInfo getField(int index) {
            return switch (index) {
                case 0 -> new FieldInfo("id", String.class, 0, null, NO_ANNOTATIONS);
                case 1 -> new FieldInfo("age", Integer.class, 0, null, NO_ANNOTATIONS);
                case 2 -> new FieldInfo("displayName", String.class, 0, "display_name", NO_ANNOTATIONS);
                default -> null;
            };
        }

        @Override
        public String[] getMethods() {
            return new String[0];
        }

        @Override
        public int getMethod(int index) {
            return -1;
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }

    /** Plain POJO that deliberately has no {@code @Alias} annotations so snake↔camel mapping is exercised. */
    private static final class PlainUser {
        private String userId;
        private String firstName;

        String getUserId() {
            return userId;
        }

        void setUserId(String userId) {
            this.userId = userId;
        }

        String getFirstName() {
            return firstName;
        }

        void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }

    private static final class PlainUserReflector implements GeneratedReflector<PlainUser> {
        private static final AnnotationMeta[] NO_ANNOTATIONS = new AnnotationMeta[0];

        @Override
        public PlainUser newInstance() {
            return new PlainUser();
        }

        @Override
        public ClassInfo getClassInfo() {
            return null;
        }

        @Override
        public Object invoke(PlainUser target, int index, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(PlainUser target, int index, Object value) {
            switch (index) {
                case 0 -> target.setUserId((String) value);
                case 1 -> target.setFirstName((String) value);
                default -> throw new UnsupportedOperationException(String.valueOf(index));
            }
        }

        @Override
        public Object get(PlainUser target, int index) {
            return switch (index) {
                case 0 -> target.getUserId();
                case 1 -> target.getFirstName();
                default -> throw new UnsupportedOperationException(String.valueOf(index));
            };
        }

        @Override
        public String[] getFields() {
            return new String[]{"userId", "firstName"};
        }

        @Override
        public FieldInfo getField(int index) {
            return switch (index) {
                case 0 -> new FieldInfo("userId", String.class, 0, null, NO_ANNOTATIONS);
                case 1 -> new FieldInfo("firstName", String.class, 0, null, NO_ANNOTATIONS);
                default -> null;
            };
        }

        @Override
        public String[] getMethods() {
            return new String[0];
        }

        @Override
        public int getMethod(int index) {
            return -1;
        }

        @Override
        public MethodInfo[] getMethod(String name) {
            return new MethodInfo[0];
        }
    }
}
