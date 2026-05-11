package com.example.simple;

import com.example.simple.entity.User;
import com.example.simple.mapper.UserMapper;
import com.nicleo.kora.core.runtime.AnnotationMeta;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.IdGenerator;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.SqlExecutionContext;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.core.runtime.SqlGenerator;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.TypeConverter;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GeneratedMapperMethodAnnotationTest {
    @Test
    void generatedMapperShouldPassCompiledMethodAnnotationsToSqlInterceptorContext() throws Exception {
        CapturingSqlExecutor sqlExecutor = new CapturingSqlExecutor();
        Class<?> supportClass = Class.forName("gen.KoraSimpleConfigGenerated");
        java.lang.reflect.Field installedField = supportClass.getDeclaredField("installed");
        installedField.setAccessible(true);
        installedField.setBoolean(null, false);
        UserMapper mapper = (UserMapper) Class.forName("com.example.simple.mapper.UserMapperImpl")
                .getConstructor(SqlExecutor.class)
                .newInstance(sqlExecutor);

        mapper.selectById(1L);

        AnnotationMeta annotation = (AnnotationMeta) SqlExecutionContext.class
                .getMethod("getMapperMethodAnnotation", String.class)
                .invoke(sqlExecutor.capturedContext, TestMapperTag.class.getName());
        assertNotNull(annotation);
        assertEquals(TestMapperTag.class.getName(), annotation.type());
        assertEquals("selectById", annotation.value("value"));
        assertEquals(7, annotation.value("order"));
        assertEquals(true, annotation.value("enabled"));
    }

    @Test
    void generatedMapperMethodShouldCopyRuntimeAnnotationsFromInterfaceMethod() throws Exception {
        java.lang.reflect.Method method = Class.forName("com.example.simple.mapper.UserMapperImpl")
                .getMethod("selectById", Long.class);

        TestMapperTag annotation = method.getAnnotation(TestMapperTag.class);

        assertNotNull(annotation);
        assertEquals("selectById", annotation.value());
        assertEquals(7, annotation.order());
        assertEquals(true, annotation.enabled());
    }

    @Test
    void generatedMapperImplShouldNotBeFinal() throws Exception {
        Class<?> mapperImpl = Class.forName("com.example.simple.mapper.UserMapperImpl");

        assertFalse(java.lang.reflect.Modifier.isFinal(mapperImpl.getModifiers()));
    }

    @Test
    void generatedUpdateMapperMethodShouldUseValidOperandStackOrder() throws Exception {
        CapturingSqlExecutor sqlExecutor = new CapturingSqlExecutor();
        UserMapper mapper = (UserMapper) Class.forName("com.example.simple.mapper.UserMapperImpl")
                .getConstructor(SqlExecutor.class)
                .newInstance(sqlExecutor);

        int rows = mapper.expireUsers(LocalDateTime.of(2026, 5, 11, 15, 44));

        assertEquals(1, rows);
        assertEquals(com.nicleo.kora.core.xml.SqlCommandType.UPDATE, sqlExecutor.capturedContext.getCommandType());
    }

    static final class CapturingSqlExecutor implements SqlExecutor {
        private TypeConverter typeConverter = new TypeConverter();
        private SqlExecutionContext capturedContext;

        @Override
        public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeConverter getTypeConverter() {
            return typeConverter;
        }

        @Override
        public void setTypeConverter(TypeConverter typeConverter) {
            this.typeConverter = typeConverter;
        }

        @Override
        public IdGenerator getIdGenerator() {
            return null;
        }

        @Override
        public void setIdGenerator(IdGenerator idGenerator) {
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            this.capturedContext = context;
            return resultType == User.class ? (T) new User() : null;
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, Object[] args, SqlExecutionContext context) {
            this.capturedContext = context;
            return 1;
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SqlPagingSupport getSqlPagingSupport() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DbType getDbType() {
            return DbType.H2;
        }

        @Override
        public SqlGenerator getSqlGenerator() {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
            throw new UnsupportedOperationException();
        }
    }
}
