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
import org.byteora.kyra.orm.runtime.DbType;
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
import java.util.concurrent.ConcurrentHashMap;

public class BaseMapperImpl<T> extends AbstractMapper<T> implements BaseMapper<T> {
    /**
     * Caches the rendered {@code insert} SQL per (entity type, dialect, ordered column set). The
     * column set is the only input to the rendered statement (placeholders are positional), so for a
     * batch of same-shaped entities the SQL is rendered once and reused. Bounded by the number of
     * distinct non-null field combinations per entity type.
     */
    private static final Map<InsertSqlKey, String> INSERT_SQL_CACHE = new ConcurrentHashMap<>();

    /**
     * Caches the rendered {@code update ... where id = ?} SQL per (entity type, dialect, ordered
     * column set). Mirrors {@link #INSERT_SQL_CACHE}: placeholders are positional and the where
     * clause is always a single id equality, so the statement depends only on the non-null column
     * set. On a cache hit the per-field {@code UpdateAssignment}/{@code LiteralExpression} wrappers
     * are skipped entirely.
     */
    private static final Map<UpdateSqlKey, String> UPDATE_BY_ID_SQL_CACHE = new ConcurrentHashMap<>();

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
        DbType dbType = sqlExecutor.getDbType();
        String sql = INSERT_SQL_CACHE.computeIfAbsent(
                new InsertSqlKey(entityClass, dbType, columns),
                key -> sqlExecutor.getSqlGenerator().renderInsert(entityTable, key.columns(), args, dbType).sql()
        );
        return new InsertSpec(sql, args.toArray(), assignGeneratedId);
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
        List<String> columns = new ArrayList<>();
        List<Object> args = new ArrayList<>();
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
            return null;
        }
        args.add(idValue);
        DbType dbType = sqlExecutor.getDbType();
        String sql = UPDATE_BY_ID_SQL_CACHE.computeIfAbsent(
                new UpdateSqlKey(entityClass, dbType, columns),
                key -> renderUpdateByIdSql(key.columns(), idValue, dbType)
        );
        return new UpdateByIdSpec(sql, args.toArray());
    }

    /**
     * Renders the {@code update ... where id = ?} statement for a given column set. Only invoked on
     * a cache miss; the literal id value is used solely to build the where condition for rendering
     * and never affects the emitted SQL (the clause renders to a positional placeholder).
     */
    private String renderUpdateByIdSql(List<String> columns, Object idValue, DbType dbType) {
        List<UpdateAssignment> updateAssignments = new ArrayList<>(columns.size());
        for (String column : columns) {
            updateAssignments.add(new UpdateAssignment(
                    entityTable.columnRef(column),
                    Expressions.literal(null)
            ));
        }
        return sqlExecutor.getSqlGenerator().renderUpdate(
                entityTable,
                new UpdateDefinition(
                        updateAssignments,
                        new WhereDefinition(Conditions.eq(entityTable.idColumn(), idValue), List.of(), null, null)
                ),
                dbType
        ).sql();
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

    /**
     * Cache key for rendered insert SQL. {@code columns} participates in equality by value (an
     * ordered {@link List}); it is built fresh per call and never mutated afterwards, so it is safe
     * to retain as a key without defensive copying.
     */
    private record InsertSqlKey(Class<?> entityType, DbType dbType, List<String> columns) {
    }

    /**
     * Cache key for rendered {@code update ... where id = ?} SQL. {@code columns} participates in
     * equality by value (an ordered {@link List}); it is built fresh per call and never mutated
     * afterwards, so it is safe to retain as a key without defensive copying.
     */
    private record UpdateSqlKey(Class<?> entityType, DbType dbType, List<String> columns) {
    }
}
