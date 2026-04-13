package com.example.simple;

import com.example.simple.dto.UserSummary;
import com.example.simple.mapper.MixedUserMapper;
import com.example.simple.mapper.MixedUserMapperImpl;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.DbType;
import com.nicleo.kora.core.runtime.IdGenerator;
import com.nicleo.kora.core.runtime.RowMapper;
import com.nicleo.kora.core.runtime.SqlExecutionContext;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.core.runtime.SqlGenerator;
import com.nicleo.kora.core.runtime.SqlPagingSupport;
import com.nicleo.kora.core.runtime.TypeConverter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class MixedCapabilityMapperGenerationTest {
    @Test
    void generatedMapperShouldResolvePerCapabilityGenericTypes() {
        MixedUserMapper mapper = new MixedUserMapperImpl(new NoopSqlExecutor());

        assertInstanceOf(MixedUserMapperImpl.class, mapper);
        assertFalse(AbstractMapper.class.isAssignableFrom(MixedUserMapperImpl.class));
        assertEquals(UserSummary.class.getName(), mapper.mappedTypeName());
    }

    static final class NoopSqlExecutor implements SqlExecutor {
        private TypeConverter typeConverter = new TypeConverter();

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
        public Object updateAndReturnGeneratedKey(String sql, Object[] args) {
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
        public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(String sql, Object[] args, SqlExecutionContext context) {
            throw new UnsupportedOperationException();
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
