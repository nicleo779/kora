package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;
import org.byteora.kyra.orm.runtime.dialect.PageClause;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;
import org.byteora.kyra.orm.runtime.dialect.SqlDialects;

import java.util.Arrays;
import java.util.List;

public class DefaultSqlPagingSupport implements SqlPagingSupport {
    @Override
    public <T> Page<T> page(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType) {
        int current = paging == null || paging.getCurrent() == null || paging.getCurrent() < 1 ? 1 : paging.getCurrent();
        int size = paging == null || paging.getSize() == null || paging.getSize() < 1 ? 10 : paging.getSize();
        long total = count(sqlExecutor, context, sql, args);
        if (total == 0L) {
            return new Page<>(current, size, 0L, List.of());
        }
        long offset = (current - 1L) * size;
        SqlRequest pageRequest = buildPageRequest(sqlExecutor, sql, args, size, offset);
        List<T> records = sqlExecutor.selectList(pageRequest.sql(), pageRequest.args(), context, elementType);
        return new Page<>(current, size, total, records);
    }

    @Override
    public long count(SqlExecutor sqlExecutor, SqlExecutionContext context, String sql, Object[] args) {
        if (context != null && context.getCountRequest() != null) {
            return executeCountRequest(sqlExecutor, context.getCountRequest());
        }
        return executeCount(sqlExecutor, sql, args);
    }

    long executeCountRequest(SqlExecutor sqlExecutor, SqlRequest countRequest) {
        List<Long> totals = sqlExecutor.executeQuery(
                countRequest.sql(),
                countRequest.args(),
                resultSet -> resultSet.getLong(1)
        );
        return totals.isEmpty() ? 0L : totals.getFirst();
    }

    long executeCount(SqlExecutor sqlExecutor, String sql, Object[] args) {
        var dialect = SqlDialects.dialect(sqlExecutor.getDbType());
        SqlRequest countRequest = dialect.countQueryRewriter().rewriteRaw(sql, args, new RenderContext(dialect));
        List<Long> totals = sqlExecutor.executeQuery(countRequest.sql(), countRequest.args(), resultSet -> resultSet.getLong(1));
        return totals.isEmpty() ? 0L : totals.getFirst();
    }

    SqlRequest buildPageRequest(SqlExecutor sqlExecutor, String sql, Object[] args, long size, long offset) {
        var dialect = SqlDialects.dialect(sqlExecutor.getDbType());
        RenderContext context = new RenderContext(dialect);
        context.sql().append(sql);
        if (args != null) {
            context.args().addAll(Arrays.asList(args));
        }
        dialect.paging().render(
                new PageClause(Math.toIntExact(offset), Math.toIntExact(size), false),
                context
        );
        return context.toRequest();
    }

    SqlRequest buildCountRequest(SqlExecutor sqlExecutor, String sql, Object[] args) {
        var dialect = SqlDialects.dialect(sqlExecutor.getDbType());
        return dialect.countQueryRewriter().rewriteRaw(sql, args, new RenderContext(dialect));
    }
}
