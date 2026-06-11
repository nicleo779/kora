package org.byteora.kyra.orm.query;

import org.byteora.kyra.orm.runtime.SqlExecutionContext;
import org.byteora.kyra.orm.runtime.SqlExecutor;
import org.byteora.kyra.orm.runtime.SqlRequest;
import org.byteora.kyra.orm.runtime.SqlExecutorException;
import org.byteora.kyra.orm.xml.SqlCommandType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class QueryWrapper {
    private final List<SqlExpression> selectExpressions = new ArrayList<>();
    private final List<JoinSpec> joins = new ArrayList<>();
    private final List<SqlExpression> groupByExpressions = new ArrayList<>();
    private final WhereWrapper whereWrapper = new WhereWrapper();
    private final SqlExecutor sqlExecutor;
    private Condition having;
    private EntityTable<?> from;
    private boolean selectAll;

    public QueryWrapper() {
        this(null);
    }

    public QueryWrapper(SqlExecutor sqlExecutor) {
        this.sqlExecutor = sqlExecutor;
    }

    public QueryWrapper select(SqlExpression... expressions) {
        selectExpressions.addAll(List.of(expressions));
        return this;
    }

    public QueryWrapper selectAll() {
        this.selectAll = true;
        return this;
    }

    public QueryWrapper from(EntityTable<?> table) {
        this.from = Objects.requireNonNull(table, "table");
        return this;
    }

    public JoinStep leftJoin(EntityTable<?> table) {
        return new JoinStep(this, "LEFT JOIN", table);
    }

    public QueryWrapper leftJoin(EntityTable<?> table, Condition on) {
        return leftJoin(table).on(on);
    }

    public QueryWrapper leftJoin(EntityTable<?> table, Consumer<PredicateBuilder> on) {
        return leftJoin(table).on(on);
    }

    public JoinStep innerJoin(EntityTable<?> table) {
        return new JoinStep(this, "INNER JOIN", table);
    }

    public QueryWrapper innerJoin(EntityTable<?> table, Condition on) {
        return innerJoin(table).on(on);
    }

    public QueryWrapper innerJoin(EntityTable<?> table, Consumer<PredicateBuilder> on) {
        return innerJoin(table).on(on);
    }

    public JoinStep rightJoin(EntityTable<?> table) {
        return new JoinStep(this, "RIGHT JOIN", table);
    }

    public QueryWrapper rightJoin(EntityTable<?> table, Condition on) {
        return rightJoin(table).on(on);
    }

    public QueryWrapper rightJoin(EntityTable<?> table, Consumer<PredicateBuilder> on) {
        return rightJoin(table).on(on);
    }

    public QueryWrapper where(Consumer<PredicateBuilder> consumer) {
        whereWrapper.where(consumer);
        return this;
    }

    public QueryWrapper where(Condition condition) {
        whereWrapper.condition(condition);
        return this;
    }

    public QueryWrapper where(Condition... conditions) {
        whereWrapper.where(conditions);
        return this;
    }

    public QueryWrapper groupBy(SqlExpression... expressions) {
        groupByExpressions.clear();
        groupByExpressions.addAll(List.of(expressions));
        return this;
    }

    public QueryWrapper groupBy(NamedSqlExpression... expressions) {
        groupByExpressions.clear();
        for (NamedSqlExpression expression : expressions) {
            groupByExpressions.add(expression.aliasRef());
        }
        return this;
    }

    public QueryWrapper groupByAlias(String... aliases) {
        groupByExpressions.clear();
        for (String alias : aliases) {
            groupByExpressions.add(Expressions.aliasRef(alias));
        }
        return this;
    }

    public QueryWrapper having(Consumer<PredicateBuilder> consumer) {
        PredicateBuilder builder = new PredicateBuilder();
        consumer.accept(builder);
        this.having = builder.build();
        return this;
    }

    public QueryWrapper having(Condition condition) {
        this.having = condition;
        return this;
    }

    public QueryWrapper having(Condition... conditions) {
        this.having = Conditions.and(conditions);
        return this;
    }

    public QueryWrapper orderBy(Consumer<OrderBuilder> consumer) {
        whereWrapper.orderBy(consumer);
        return this;
    }

    public QueryWrapper limit(int limit) {
        whereWrapper.limit(limit);
        return this;
    }

    public QueryWrapper limit(int offset, int limit) {
        whereWrapper.limit(offset, limit);
        return this;
    }

    public QueryDefinition toDefinition() {
        if (from == null) {
            throw new SqlExecutorException("QueryWrapper requires from(table) before rendering SQL");
        }
        return new QueryDefinition(
                List.copyOf(selectExpressions),
                selectAll,
                from,
                joins.stream().map(join -> new QueryJoin(join.joinType(), join.table(), join.on())).toList(),
                List.copyOf(groupByExpressions),
                having,
                whereWrapper.toDefinition()
        );
    }

    public <T> T one(Class<T> resultType) {
        var sqlExecutor = requireSqlExecutor();
        QueryDefinition definition = toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(definition, sqlExecutor.getDbType());
        return sqlExecutor.selectOne(request.sql(), request.args(), resultType);

    }

    public <T> List<T> list(Class<T> resultType) {
        var sqlExecutor = requireSqlExecutor();
        QueryDefinition definition = toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(definition, sqlExecutor.getDbType());
        return sqlExecutor.selectList(request.sql(), request.args(), resultType);
    }

    public long count() {
        var sqlExecutor = requireSqlExecutor();
        QueryDefinition definition = toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(definition, sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(definition, sqlExecutor.getDbType());
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(QueryWrapper.class, "count")
                .resultType(Long.class)
                .countRequest(countRequest)
                .build();
        return sqlExecutor.getSqlPagingSupport().count(sqlExecutor, context, request.sql(), request.args());
    }

    public <T> Page<T> page(Paging paging, Class<T> resultType) {
        var sqlExecutor = requireSqlExecutor();
        QueryDefinition definition = toDefinition();
        SqlRequest request = sqlExecutor.getSqlGenerator().renderQuery(definition, sqlExecutor.getDbType());
        SqlRequest countRequest = sqlExecutor.getSqlGenerator().rewriteCount(definition, sqlExecutor.getDbType());
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(QueryWrapper.class, "page")
                .resultType(resultType)
                .paging(paging)
                .countRequest(countRequest)
                .build();
        return sqlExecutor.getSqlPagingSupport().page(sqlExecutor, context, request.sql(), request.args(), paging, resultType);
    }

    private SqlExecutor requireSqlExecutor() {
        if (sqlExecutor != null) {
            return this.sqlExecutor;
        }
        throw new SqlExecutorException("QueryWrapper is not bound to a SqlExecutor. Use new QueryWrapper(sqlExecutor), new QueryWrapper(provider), or Wrapper.query(sqlExecutor).");
    }

    private void addJoin(String joinType, EntityTable<?> table, Condition on) {
        joins.add(new JoinSpec(joinType, table, on));
    }

    public static final class JoinStep {
        private final QueryWrapper owner;
        private final String joinType;
        private final EntityTable<?> table;

        private JoinStep(QueryWrapper owner, String joinType, EntityTable<?> table) {
            this.owner = owner;
            this.joinType = joinType;
            this.table = table;
        }

        public QueryWrapper on(Condition condition) {
            owner.addJoin(joinType, table, condition);
            return owner;
        }

        public QueryWrapper on(Consumer<PredicateBuilder> consumer) {
            PredicateBuilder builder = new PredicateBuilder();
            consumer.accept(builder);
            return on(builder.build());
        }
    }

    private record JoinSpec(String joinType, EntityTable<?> table, Condition on) {
    }

}
