package com.nicleo.kora.core.mapper;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.query.QueryDefinition;
import com.nicleo.kora.core.query.Tables;
import com.nicleo.kora.core.query.UpdateWrapper;
import com.nicleo.kora.core.query.UpdateAssignment;
import com.nicleo.kora.core.query.UpdateDefinition;
import com.nicleo.kora.core.query.WhereWrapper;
import com.nicleo.kora.core.query.WhereDefinition;
import com.nicleo.kora.core.query.Conditions;
import com.nicleo.kora.core.query.Expressions;
import com.nicleo.kora.core.runtime.AbstractMapper;
import com.nicleo.kora.core.runtime.FieldInfo;
import com.nicleo.kora.core.runtime.GeneratedReflector;
import com.nicleo.kora.core.runtime.GeneratedReflectors;
import com.nicleo.kora.core.runtime.DefaultIdGenerator;
import com.nicleo.kora.core.runtime.SqlExecutionContext;
import com.nicleo.kora.core.runtime.SqlExecutor;
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.SqlExecutorException;
import com.nicleo.kora.core.xml.SqlCommandType;

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
        return sqlExecutor.selectOne(request.getSql(), request.getArgs(), entityClass);
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
        return sqlExecutor.selectList(request.getSql(), request.getArgs(), entityClass);
    }

    @Override
    public List<T> selectList(WhereWrapper query) {
        if (query == null) query = new WhereWrapper();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlExecutor.getDbType());
        return sqlExecutor.selectList(request.getSql(), request.getArgs(), entityClass);
    }

    @Override
    public T selectOne(WhereWrapper query) {
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlExecutor.getDbType());
        return sqlExecutor.selectOne(request.getSql(), request.getArgs(), entityClass);
    }

    @Override
    public long count(WhereWrapper query) {
        if (query == null) query = new WhereWrapper();
        var whereDefinition = query.toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(
                new com.nicleo.kora.core.query.QueryDefinition(
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
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                getClass().getName(),
                "count",
                SqlCommandType.SELECT,
                Long.class,
                null,
                countRequest,
                true
        );
        return sqlExecutor.getSqlPagingSupport().count(sqlExecutor, context, request.getSql(), request.getArgs());
    }

    @Override
    public Page<T> page(Paging paging, WhereWrapper query) {
        if (query == null) query = new WhereWrapper();
        var whereDefinition = query.toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderSelect(entityTable, whereDefinition, sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(
                new com.nicleo.kora.core.query.QueryDefinition(
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
        SqlExecutionContext context = new SqlExecutionContext(
                sqlExecutor,
                getClass().getName(),
                "page",
                SqlCommandType.SELECT,
                entityClass,
                paging,
                countRequest,
                true
        );
        return sqlExecutor.getSqlPagingSupport().page(sqlExecutor, context, request.getSql(), request.getArgs(), paging, entityClass);
    }

    @Override
    public int insert(T entity) {
        InsertSpec spec = buildInsertSpec(entity);
        if (spec.assignGeneratedId()) {
            Object generatedKey = sqlExecutor.updateAndReturnGeneratedKey(spec.sql(), spec.args(), entityTable.idColumn().javaType());
            GeneratedReflector<T> reflector = GeneratedReflectors.get(entityClass);
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
        return sqlExecutor.update(request.getSql(), request.getArgs());
    }

    @Override
    public int update(UpdateWrapper updateWrapper) {
        SqlRequest request = sqlExecutor.getSqlGenerator().renderUpdate(entityTable, updateWrapper.toDefinition(), sqlExecutor.getDbType());
        return sqlExecutor.update(request.getSql(), request.getArgs());
    }

    @Override
    public int deleteById(Serializable id) {
        SqlRequest request = sqlExecutor.getSqlGenerator().renderDelete(
                entityTable,
                new WhereDefinition(Conditions.eq(entityTable.idColumn(), id), List.of(), null, null),
                sqlExecutor.getDbType()
        );
        return sqlExecutor.update(request.getSql(), request.getArgs());
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
        return sqlExecutor.update(request.getSql(), request.getArgs());
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
        GeneratedReflector<T> reflector = GeneratedReflectors.get(entityClass);
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
        return new InsertSpec(request.getSql(), request.getArgs(), assignGeneratedId);
    }

    private boolean appendInsertId(T entity, GeneratedReflector<T> reflector, int idFieldIndex, List<String> columns, List<Object> args) {
        if (entityTable.idStrategy() == com.nicleo.kora.core.annotation.IdStrategy.NONE) {
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
        GeneratedReflector<T> reflector = GeneratedReflectors.get(entityClass);
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
        return new UpdateByIdSpec(request.getSql(), request.getArgs());
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
