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
}
