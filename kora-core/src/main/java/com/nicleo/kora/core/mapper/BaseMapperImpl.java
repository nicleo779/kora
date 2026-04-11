package com.nicleo.kora.core.mapper;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.query.QueryDefinition;
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
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.SqlSession;
import com.nicleo.kora.core.runtime.SqlSessionException;
import com.nicleo.kora.core.xml.SqlCommandType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BaseMapperImpl<T> extends AbstractMapper<T> implements BaseMapper<T> {
    public BaseMapperImpl(SqlSession sqlSession, EntityTable<T> entityTable) {
        super(sqlSession, entityTable);
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
        SqlRequest request = sqlSession.getSqlGenerator().renderQuery(queryDefinition, sqlSession.getDbType());
        return sqlSession.selectOne(request.getSql(), request.getArgs(), entityClass);
    }

    @Override
    public List<T> selectByIds(Collection<Serializable> ids) {
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
        SqlRequest request = sqlSession.getSqlGenerator().renderQuery(queryDefinition, sqlSession.getDbType());
        return sqlSession.selectList(request.getSql(), request.getArgs(), entityClass);
    }

    @Override
    public List<T> selectList(WhereWrapper query) {
        SqlRequest request = sqlSession.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlSession.getDbType());
        return sqlSession.selectList(request.getSql(), request.getArgs(), entityClass);
    }

    @Override
    public T selectOne(WhereWrapper query) {
        SqlRequest request = sqlSession.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlSession.getDbType());
        return sqlSession.selectOne(request.getSql(), request.getArgs(), entityClass);
    }

    @Override
    public long count(WhereWrapper query) {
        var whereDefinition = query.toDefinition();
        SqlRequest request = sqlSession.getSqlGenerator().renderSelect(entityTable, query.toDefinition(), sqlSession.getDbType());
        SqlRequest countRequest = sqlSession.getSqlGenerator().rewriteCount(
                new com.nicleo.kora.core.query.QueryDefinition(
                        java.util.List.of(),
                        true,
                        entityTable,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        whereDefinition
                ),
                sqlSession.getDbType()
        );
        SqlExecutionContext context = new SqlExecutionContext(
                sqlSession,
                getClass().getName(),
                "count",
                SqlCommandType.SELECT,
                Long.class,
                null,
                countRequest,
                true
        );
        return sqlSession.getSqlPagingSupport().count(sqlSession, context, request.getSql(), request.getArgs());
    }

    @Override
    public Page<T> page(Paging paging, WhereWrapper query) {
        var whereDefinition = query.toDefinition();
        SqlRequest request = sqlSession.getSqlGenerator().renderSelect(entityTable, whereDefinition, sqlSession.getDbType());
        SqlRequest countRequest = sqlSession.getSqlGenerator().rewriteCount(
                new com.nicleo.kora.core.query.QueryDefinition(
                        java.util.List.of(),
                        true,
                        entityTable,
                        java.util.List.of(),
                        java.util.List.of(),
                        null,
                        whereDefinition
                ),
                sqlSession.getDbType()
        );
        SqlExecutionContext context = new SqlExecutionContext(
                sqlSession,
                getClass().getName(),
                "page",
                SqlCommandType.SELECT,
                entityClass,
                paging,
                countRequest,
                true
        );
        return sqlSession.getSqlPagingSupport().page(sqlSession, context, request.getSql(), request.getArgs(), paging, entityClass);
    }

    @Override
    public int insert(T entity) {
        InsertSpec spec = buildInsertSpec(entity);
        if (spec.assignGeneratedId()) {
            Object generatedKey = sqlSession.updateAndReturnGeneratedKey(spec.sql(), spec.args());
            assignGeneratedId(entity, GeneratedReflectors.get(entityClass), generatedKey);
            return generatedKey == null ? 0 : 1;
        }
        return sqlSession.update(spec.sql(), spec.args());
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
        return sqlSession.update(spec.sql(), spec.args());
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
        SqlRequest request = sqlSession.getSqlGenerator().renderDelete(entityTable, query.toDefinition(), sqlSession.getDbType());
        return sqlSession.update(request.getSql(), request.getArgs());
    }

    @Override
    public int update(UpdateWrapper updateWrapper) {
        SqlRequest request = sqlSession.getSqlGenerator().renderUpdate(entityTable, updateWrapper.toDefinition(), sqlSession.getDbType());
        return sqlSession.update(request.getSql(), request.getArgs());
    }

    @Override
    public int deleteById(Serializable id) {
        SqlRequest request = sqlSession.getSqlGenerator().renderDelete(
                entityTable,
                new WhereDefinition(Conditions.eq(entityTable.idColumn(), id), List.of(), null, null),
                sqlSession.getDbType()
        );
        return sqlSession.update(request.getSql(), request.getArgs());
    }

    @Override
    public int deleteByIds(Collection<Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        SqlRequest request = sqlSession.getSqlGenerator().renderDelete(
                entityTable,
                new WhereDefinition(Conditions.in(entityTable.idColumn(), ids), List.of(), null, null),
                sqlSession.getDbType()
        );
        return sqlSession.update(request.getSql(), request.getArgs());
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
        List<String> columns = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        boolean assignGeneratedId = appendInsertId(entity, reflector, columns, args);
        for (String field : reflector.fieldNamesView()) {
            if (isIdField(field)) {
                continue;
            }
            Object value = reflector.get(entity, field);
            if (value == null) {
                continue;
            }
            columns.add(entityTable.columnName(field));
            args.add(value);
        }
        if (columns.isEmpty()) {
            throw new SqlSessionException("Insert requires at least one non-null field: " + entityClass.getName());
        }
        try {
            SqlRequest request = sqlSession.getSqlGenerator().renderInsert(entityTable, columns, args, sqlSession.getDbType());
            return new InsertSpec(request.getSql(), request.getArgs(), assignGeneratedId);
        } catch (RuntimeException ex) {
            StringBuilder sql = new StringBuilder("insert into ")
                    .append(entityTable.tableName())
                    .append(" (")
                    .append(String.join(", ", columns))
                    .append(") values (");
            appendPlaceholders(sql, columns.size());
            sql.append(')');
            return new InsertSpec(sql.toString(), args.toArray(), assignGeneratedId);
        }
    }

    private boolean appendInsertId(T entity, GeneratedReflector<T> reflector, List<String> columns, List<Object> args) {
        if (entityTable.idStrategy() == com.nicleo.kora.core.annotation.IdStrategy.NONE) {
            return reflector.get(entity, idFieldName()) == null;
        }
        String idFieldName = idFieldName();
        Object idValue = reflector.get(entity, idFieldName);
        if (idValue == null) {
            idValue = generateId(entity);
        }
        if (idValue != null) {
            reflector.set(entity, idFieldName, idValue);
            columns.add(entityTable.idColumn().columnName());
            args.add(idValue);
        }
        return false;
    }

    private void assignGeneratedId(T entity, GeneratedReflector<T> reflector, Object generatedKey) {
        if (generatedKey == null) {
            return;
        }
        FieldInfo idField = reflector.getField(idFieldName());
        Object converted = generatedKey;
        if (idField != null && idField.type() instanceof Class<?> targetType) {
            converted = sqlSession.getTypeConverter().cast(generatedKey, targetType);
        }
        reflector.set(entity, idFieldName(), converted);
    }

    private Object generateId(T entity) {
        return DefaultIdGenerator.INSTANCE.generate(sqlSession, entityTable, entity);
    }

    private UpdateByIdSpec buildUpdateByIdSpec(T entity) {
        GeneratedReflector<T> reflector = GeneratedReflectors.get(entityClass);
        Object idValue = reflector.get(entity, idFieldName());
        if (idValue == null) {
            throw new SqlSessionException("Update by id requires non-null id field: " + entityClass.getName());
        }
        List<UpdateAssignment> updateAssignments = new ArrayList<>();
        for (String field : reflector.fieldNamesView()) {
            if (isIdField(field)) {
                continue;
            }
            Object value = reflector.get(entity, field);
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
        try {
            SqlRequest request = sqlSession.getSqlGenerator().renderUpdate(
                    entityTable,
                    new UpdateDefinition(
                            updateAssignments,
                            new WhereDefinition(Conditions.eq(entityTable.idColumn(), idValue), List.of(), null, null)
                    ),
                    sqlSession.getDbType()
            );
            return new UpdateByIdSpec(request.getSql(), request.getArgs());
        } catch (RuntimeException ex) {
            List<Object> args = new ArrayList<>();
            List<String> assignments = new ArrayList<>();
            for (String field : reflector.fieldNamesView()) {
                if (isIdField(field)) {
                    continue;
                }
                Object value = reflector.get(entity, field);
                if (value == null) {
                    continue;
                }
                assignments.add(entityTable.columnName(field) + " = ?");
                args.add(value);
            }
            args.add(idValue);
            String sql = "update " + entityTable.tableName()
                    + " set " + String.join(", ", assignments)
                    + " where " + entityTable.idColumn().columnName() + " = ?";
            return new UpdateByIdSpec(sql, args.toArray());
        }
    }

    private int executeGroupedBatch(Map<String, List<Object[]>> batchGroups) {
        int total = 0;
        for (Map.Entry<String, List<Object[]>> entry : batchGroups.entrySet()) {
            int[] results = sqlSession.executeBatch(entry.getKey(), entry.getValue());
            total += Arrays.stream(results).sum();
        }
        return total;
    }

    private record InsertSpec(String sql, Object[] args, boolean assignGeneratedId) {
    }

    private record UpdateByIdSpec(String sql, Object[] args) {
    }
}
