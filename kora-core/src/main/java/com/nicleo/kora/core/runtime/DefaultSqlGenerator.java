package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.QueryDefinition;
import com.nicleo.kora.core.query.UpdateDefinition;
import com.nicleo.kora.core.query.WhereDefinition;
import com.nicleo.kora.core.runtime.dialect.DefaultSqlDialectRegistry;
import com.nicleo.kora.core.runtime.dialect.DeleteModel;
import com.nicleo.kora.core.runtime.dialect.QueryModelMapper;
import com.nicleo.kora.core.runtime.dialect.QueryModel;
import com.nicleo.kora.core.runtime.dialect.RenderContext;
import com.nicleo.kora.core.runtime.dialect.SqlDialect;
import com.nicleo.kora.core.runtime.dialect.SqlDialectRegistry;
import com.nicleo.kora.core.runtime.dialect.UpdateModel;

import java.util.List;

public class DefaultSqlGenerator implements SqlGenerator {
    private final SqlDialectRegistry dialectRegistry;

    public DefaultSqlGenerator() {
        this(new DefaultSqlDialectRegistry());
    }

    public DefaultSqlGenerator(SqlDialectRegistry dialectRegistry) {
        this.dialectRegistry = dialectRegistry;
    }

    @Override
    public SqlRequest renderQuery(QueryDefinition definition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.queryRenderer().render(QueryModelMapper.from(definition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest rewriteCount(QueryDefinition definition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.countQueryRewriter().rewrite(QueryModelMapper.from(definition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest renderSelect(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        QueryDefinition definition = new QueryDefinition(
                java.util.List.of(),
                true,
                table,
                java.util.List.of(),
                java.util.List.of(),
                null,
                whereDefinition
        );
        return renderQuery(definition, dbType);
    }

    @Override
    public SqlRequest renderDelete(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.deleteRenderer().render(new DeleteModel(table, whereDefinition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest renderUpdate(EntityTable<?> table, UpdateDefinition updateDefinition, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.updateRenderer().render(new UpdateModel(table, updateDefinition), new RenderContext(dialect));
    }

    @Override
    public SqlRequest renderInsert(EntityTable<?> table, List<String> columns, List<Object> args, DbType dbType) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        return dialect.insertRenderer().render(
                new com.nicleo.kora.core.runtime.dialect.InsertModel(table, columns, args),
                new RenderContext(dialect)
        );
    }

    public SqlRequest appendPaging(String sql, Object[] args, DbType dbType, boolean dataChange, Integer limit, Integer offset) {
        SqlDialect dialect = dialectRegistry.require(dbType);
        RenderContext context = new RenderContext(dialect);
        context.sql().append(sql);
        if (args != null) {
            context.args().addAll(List.of(args));
        }
        dialect.paging().render(new com.nicleo.kora.core.runtime.dialect.PageClause(offset, limit, dataChange), context);
        return context.toRequest();
    }
}
