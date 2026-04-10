package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.Page;
import com.nicleo.kora.core.query.Paging;
import com.nicleo.kora.core.runtime.jdbc.DefaultSqlSession;

import java.util.List;

public class DefaultSqlPagingSupport implements SqlPagingSupport {
    @Override
    public <T> Page<T> page(SqlSession sqlSession, SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType) {
        int current = paging == null || paging.getIndex() == null || paging.getIndex() < 1 ? 1 : paging.getIndex();
        int size = paging == null || paging.getSize() == null || paging.getSize() < 1 ? 10 : paging.getSize();
        long total = executeCount(sqlSession, sql, args);
        if (total == 0L) {
            return new Page<>(current, size, 0L, List.of());
        }
        long offset = (current - 1L) * size;
        String pageSql = sql + " LIMIT ? OFFSET ?";
        Object[] pageArgs = appendPagingArgs(args, size, offset);
        List<T> records = sqlSession.selectList(pageSql, pageArgs, context, elementType);
        return new Page<>(current, size, total, records);
    }

    long executeCount(SqlSession sqlSession, String sql, Object[] args) {
        if (!(sqlSession instanceof DefaultSqlSession defaultSqlSession)) {
            throw new SqlSessionException("Paging requires DefaultSqlSession for count query support");
        }
        String countSql = buildCountSql(sql);
        Object[] countArgs = trimArgsForCount(countSql, args);
        List<Long> totals = defaultSqlSession.executeQuery(countSql, countArgs, resultSet -> resultSet.getLong(1));
        return totals.isEmpty() ? 0L : totals.getFirst();
    }

    String buildCountSql(String sql) {
        String normalized = normalizeSql(sql);
        int fromIndex = topLevelKeywordIndex(normalized, " from ");
        if (fromIndex < 0 || !canUseDirectCount(normalized, fromIndex)) {
            return "select count(*) from (" + normalized + ") kora_page_total";
        }
        int cutIndex = firstPositive(
                topLevelKeywordIndex(normalized, " order by "),
                topLevelKeywordIndex(normalized, " limit "),
                topLevelKeywordIndex(normalized, " offset ")
        );
        String fromClause = cutIndex < 0 ? normalized.substring(fromIndex) : normalized.substring(fromIndex, cutIndex);
        return "select count(*)" + fromClause;
    }

    Object[] appendPagingArgs(Object[] args, long size, long offset) {
        Object[] source = args == null ? new Object[0] : args;
        Object[] merged = new Object[source.length + 2];
        System.arraycopy(source, 0, merged, 0, source.length);
        merged[source.length] = size;
        merged[source.length + 1] = offset;
        return merged;
    }

    protected boolean canUseDirectCount(String sql, int fromIndex) {
        String selectClause = sql.substring(0, fromIndex).toLowerCase();
        return topLevelKeywordIndex(sql, " group by ") < 0
                && topLevelKeywordIndex(sql, " having ") < 0
                && topLevelKeywordIndex(sql, " union ") < 0
                && topLevelKeywordIndex(sql, " union all ") < 0
                && !selectClause.contains(" distinct ");
    }

    protected int topLevelKeywordIndex(String sql, String keyword) {
        String lower = sql.toLowerCase();
        int depth = 0;
        for (int i = 0; i <= lower.length() - keyword.length(); i++) {
            char current = lower.charAt(i);
            if (current == '(') {
                depth++;
                continue;
            }
            if (current == ')') {
                depth = Math.max(0, depth - 1);
                continue;
            }
            if (depth == 0 && lower.startsWith(keyword, i)) {
                return i;
            }
        }
        return -1;
    }

    protected int firstPositive(int... indexes) {
        int result = -1;
        for (int index : indexes) {
            if (index < 0) {
                continue;
            }
            if (result < 0 || index < result) {
                result = index;
            }
        }
        return result;
    }

    protected String normalizeSql(String sql) {
        return sql == null ? "" : sql.trim().replaceAll("\\s+", " ");
    }

    private Object[] trimArgsForCount(String countSql, Object[] args) {
        Object[] source = args == null ? new Object[0] : args;
        int expectedArgs = placeholderCount(countSql);
        if (expectedArgs >= source.length) {
            return source;
        }
        Object[] trimmed = new Object[expectedArgs];
        System.arraycopy(source, 0, trimmed, 0, expectedArgs);
        return trimmed;
    }

    private int placeholderCount(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
}
