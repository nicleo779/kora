package org.byteora.kyra.orm.mapper;

import org.byteora.kyra.orm.query.EntityTable;
import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;
import org.byteora.kyra.orm.query.QueryDefinition;
import org.byteora.kyra.orm.query.Tables;
import org.byteora.kyra.orm.query.UpdateWrapper;
import org.byteora.kyra.orm.query.UpdateAssignment;
import org.byteora.kyra.orm.query.UpdateDefinition;
import org.byteora.kyra.orm.query.WhereWrapper;
import org.byteora.kyra.orm.query.WhereDefinition;
import org.byteora.kyra.orm.query.Conditions;
import org.byteora.kyra.orm.query.Expressions;
import org.byteora.kyra.orm.runtime.AbstractMapper;
import org.byteora.kyra.core.runtime.Reflector;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import org.byteora.kyra.orm.runtime.DefaultIdGenerator;
import org.byteora.kyra.orm.runtime.SqlExecutionContext;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlRequest;
import org.byteora.kyra.orm.runtime.SqlExecutorException;
import org.byteora.kyra.orm.xml.SqlCommandType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BaseMapperImpl<T> extends AbstractMapper<T> implements BaseMapper<T> {
    protected final EntityTable<T> entityTable;

    public BaseMapperImpl(SqlExecutor sqlExecutor, Class<T> entityClass) {
        super(sqlExecutor, entityClass);
        this.entityTable = Tables.get(entityClass);
    }

    @Override
    public T selectById(Serializable id) {
        QueryDefinition queryDefinition = new QueryDefinition(
                List.of(),
                true,
                entityTable,
                List.of(),
                List.of(),
                null,
                new WhereDefinition(Conditions.eq(entityTable.idColumn(), id), List.of(), null, null)
        );
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(queryDefinition, sqlExecutor.getDbType());
        return sqlExecutor.selectOne(request.sql(), request.args(), entityClass);
    }

    @Override
    public List<T> selectByIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        QueryDefinition queryDefinition = new QueryDefinition(
                List.of(),
                true,
                entityTable,
                List.of(),
                List.of(),
                null,
                new WhereDefinition(Conditions.in(entityTable.idColumn(), ids), List.of(), null, null)
        );
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(queryDefinition, sqlExecutor.getDbType());
        return sqlExecutor.selectList(request.sql(), request.args(), entityClass);
    }

    @Override
    public List<T> selectList(WhereWrapper query) {
        if (query == null) query = new WhereWrapper();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlExecutor.getDbType());
        return sqlExecutor.selectList(request.sql(), request.args(), entityClass);
    }

    @Override
    public T selectOne(WhereWrapper query) {
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlExecutor.getDbType());
        return sqlExecutor.selectOne(request.sql(), request.args(), entityClass);
    }

    @Override
    public long count(WhereWrapper query) {
        if (query == null) query = new WhereWrapper();
        var whereDefinition = query.toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(
                new org.byteora.kyra.orm.query.QueryDefinition(
                        java.util.List.of(),
                        true,
                        entityTable,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        whereDefinition
                ),
                sqlExecutor.getDbType()
        );
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(getClass(), "count")
                .resultType(Long.class)
                .countRequest(countRequest)
                .build();
        return sqlExecutor.getSqlPagingSupport().count(sqlExecutor, context, request.sql(), request.args());
    }

    @Override
    public Page<T> page(Paging paging, WhereWrapper query) {
        if (query == null) query = new WhereWrapper();
        var whereDefinition = query.toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, whereDefinition, sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(
                new org.byteora.kyra.orm.query.QueryDefinition(
                        java.util.List.of(),
                        true,
                        entityTable,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        whereDefinition
                ),
                sqlExecutor.getDbType()
        );
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(getClass(), "page")
                .resultType(entityClass)
                .paging(paging)
                .countRequest(countRequest)
                .build();
        return sqlExecutor.getSqlPagingSupport().page(sqlExecutor, context, request.sql(), request.args(), paging, entityClass);
    }

    @Override
    public int insert(T entity) {
        InsertSpec spec = buildInsertSpec(entity);
        if (spec.assignGeneratedId()) {
            Object generatedKey = sqlExecutor.updateAndReturnGeneratedKey(spec.sql(), spec.args(), entityTable.idColumn().javaType());
            Reflector<T> reflector = ReflectorRegistry.get(entityClass);
            int idFieldIndex = reflector.fieldIndex(idFieldName());
            reflector.set(entity, idFieldIndex, generatedKey);
            return generatedKey == null ? 0 : 1;
        }
        return sqlExecutor.update(spec.sql(), spec.args());
    }

    @Override
    public int insert(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        Map<String, List<Object[]>> batchGroups = new LinkedHashMap<>();
        for (T entity : entities) {
            InsertSpec spec = buildInsertSpec(entity);
            batchGroups.computeIfAbsent(spec.sql(), ignored -> new ArrayList<>()).add(spec.args());
        }
        return executeGroupedBatch(batchGroups);
    }

    @Override
    public int updateById(T entity) {
        UpdateByIdSpec spec = buildUpdateByIdSpec(entity);
        if (spec == null) {
            return 0;
        }
        return sqlExecutor.update(spec.sql(), spec.args());
    }

    @Override
    public int updateById(Collection<T> entities) {
        if (entities == null || entities.isEmpty()) {
            return 0;
        }
        Map<String, List<Object[]>> batchGroups = new LinkedHashMap<>();
        for (T entity : entities) {
            UpdateByIdSpec spec = buildUpdateByIdSpec(entity);
            if (spec == null) {
                continue;
            }
            batchGroups.computeIfAbsent(spec.sql(), ignored -> new ArrayList<>()).add(spec.args());
        }
        return executeGroupedBatch(batchGroups);
    }

    @Override
    public int delete(WhereWrapper query) {
        SqlRequest request = sqlExecutor.getSqlGenerator().renderDelete(entityTable, query.toDefinition(), sqlExecutor.getDbType());
        return sqlExecutor.update(request.sql(), request.args());
    }

    @Override
    public int update(UpdateWrapper updateWrapper) {
        SqlRequest request = sqlExecutor.getSqlGenerator().renderUpdate(entityTable, updateWrapper.toDefinition(), sqlExecutor.getDbType());
        return sqlExecutor.update(request.sql(), request.args());
    }

    @Override
    public int deleteById(Serializable id) {
        SqlRequest request = sqlExecutor.getSqlGenerator().renderDelete(
                entityTable,
                new WhereDefinition(Conditions.eq(entityTable.idColumn(), id), List.of(), null, null),
                sqlExecutor.getDbType()
        );
        return sqlExecutor.update(request.sql(), request.args());
    }

    @Override
    public int deleteByIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        SqlRequest request = sqlExecutor.getSqlGenerator().renderDelete(
                entityTable,
                new WhereDefinition(Conditions.in(entityTable.idColumn(), ids), List.of(), null, null),
                sqlExecutor.getDbType()
        );
        return sqlExecutor.update(request.sql(), request.args());
    }

    private boolean isIdField(String field) {
        return idFieldName().equals(field);
    }

    private String idFieldName() {
        return entityTable.fieldName(entityTable.idColumn().columnName());
    }

    private void appendPlaceholders(StringBuilder sql, int count) {
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append('?');
        }
    }

    private InsertSpec buildInsertSpec(T entity) {
        Reflector<T> reflector = ReflectorRegistry.get(entityClass);
        String[] fields = reflector.getFields();
        int idFieldIndex = reflector.fieldIndex(idFieldName());
        List<String> columns = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        boolean assignGeneratedId = appendInsertId(entity, reflector, idFieldIndex, columns, args);
        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            if (fieldIndex == idFieldIndex) {
                continue;
            }
            String field = fields[fieldIndex];
            Object value = reflector.get(entity, fieldIndex);
            if (value == null) {
                continue;
            }
            columns.add(entityTable.columnName(field));
            args.add(value);
        }
        if (columns.isEmpty()) {
            throw new SqlExecutorException("Insert requires at least one non-null field: " + entityClass.getName());
        }
        SqlRequest request = sqlExecutor.getSqlGenerator().renderInsert(entityTable, columns, args, sqlExecutor.getDbType());
        return new InsertSpec(request.sql(), request.args(), assignGeneratedId);
    }

    private boolean appendInsertId(T entity, Reflector<T> reflector, int idFieldIndex, List<String> columns, List<Object> args) {
        if (entityTable.idStrategy() == org.byteora.kyra.orm.annotation.IdStrategy.NONE) {
            Object idValue = reflector.get(entity, idFieldIndex);
            if (idValue != null) {
                columns.add(entityTable.idColumn().columnName());
                args.add(idValue);
                return false;
            }
            return true;
        }
        Object idValue = reflector.get(entity, idFieldIndex);
        if (idValue == null) {
            idValue = generateId(entity);
        }
        if (idValue != null) {
            reflector.set(entity, idFieldIndex, idValue);
            columns.add(entityTable.idColumn().columnName());
            args.add(idValue);
        }
        return false;
    }

    private Object generateId(T entity) {
        return DefaultIdGenerator.INSTANCE.generate(sqlExecutor, entityTable, entity);
    }

    private UpdateByIdSpec buildUpdateByIdSpec(T entity) {
        Reflector<T> reflector = ReflectorRegistry.get(entityClass);
        String[] fields = reflector.getFields();
        int idFieldIndex = reflector.fieldIndex(idFieldName());
        Object idValue = reflector.get(entity, idFieldIndex);
        if (idValue == null) {
            throw new SqlExecutorException("Update by id requires non-null id field: " + entityClass.getName());
        }
        List<UpdateAssignment> updateAssignments = new ArrayList<>();
        for (int fieldIndex = 0; fieldIndex < fields.length; fieldIndex++) {
            if (fieldIndex == idFieldIndex) {
                continue;
            }
            String field = fields[fieldIndex];
            Object value = reflector.get(entity, fieldIndex);
            if (value == null) {
                continue;
            }
            updateAssignments.add(new UpdateAssignment(
                    entityTable.columnRef(entityTable.columnName(field)),
                    Expressions.literal(value)
            ));
        }
        if (updateAssignments.isEmpty()) {
            return null;
        }
        SqlRequest request = sqlExecutor.getSqlGenerator().renderUpdate(
                entityTable,
                new UpdateDefinition(
                        updateAssignments,
                        new WhereDefinition(Conditions.eq(entityTable.idColumn(), idValue), List.of(), null, null)
                ),
                sqlExecutor.getDbType()
        );
        return new UpdateByIdSpec(request.sql(), request.args());
    }

    private int executeGroupedBatch(Map<String, List<Object[]>> batchGroups) {
        int total = 0;
        for (Map.Entry<String, List<Object[]>> entry : batchGroups.entrySet()) {
            int[] results = sqlExecutor.executeBatch(entry.getKey(), entry.getValue());
            total += Arrays.stream(results).sum();
        }
        return total;
    }

    private record InsertSpec(String sql, Object[] args, boolean assignGeneratedId) {
    }

    private record UpdateByIdSpec(String sql, Object[] args) {
    }
}
