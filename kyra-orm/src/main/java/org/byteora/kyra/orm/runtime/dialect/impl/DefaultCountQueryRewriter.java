package org.byteora.kyra.orm.runtime.dialect.impl;

import org.byteora.kyra.orm.runtime.SqlRequest;
import org.byteora.kyra.orm.runtime.dialect.CountQueryRewriter;
import org.byteora.kyra.orm.runtime.dialect.QueryModel;
import org.byteora.kyra.orm.runtime.dialect.RenderContext;

public final class DefaultCountQueryRewriter implements CountQueryRewriter {
    private static final String COUNT_SUBQUERY_ALIAS = "_count";

    @Override
    public SqlRequest rewrite(QueryModel queryModel, RenderContext context) {
        SqlRequest query = context.dialect().queryRenderer().render(queryModel, context);
        // count 与排序无关，去掉顶层 ORDER BY，避免 SQL Server 等不允许派生表内裸 ORDER BY 而报错
        return wrapSubqueryCount(new SqlRequest(stripTrailingOrderBy(query.sql()), query.args()));
    }

    @Override
    public SqlRequest rewriteRaw(String sql, Object[] args, RenderContext context) {
        String normalized = normalizeSql(sql);
        int fromIndex = topLevelKeywordIndex(normalized, " from ");
        if (fromIndex < 0 || !canUseDirectCount(normalized, fromIndex)) {
            // count 与排序无关，去掉顶层 ORDER BY，避免 SQL Server 等不允许派生表内裸 ORDER BY 而报错
            return wrapSubqueryCount(new SqlRequest(stripTrailingOrderBy(normalized), args));
        }
        int cutIndex = firstPositive(
                topLevelKeywordIndex(normalized, " order by "),
                topLevelKeywordIndex(normalized, " limit "),
                topLevelKeywordIndex(normalized, " offset ")
        );
        String fromClause = cutIndex < 0 ? normalized.substring(fromIndex) : normalized.substring(fromIndex, cutIndex);
        return new SqlRequest("select count(*)" + fromClause, trimArgsForCount("select count(*)" + fromClause, args));
    }

    private static SqlRequest wrapSubqueryCount(SqlRequest query) {
        return new SqlRequest(
                "select count(*) from (" + query.sql() + ") " + COUNT_SUBQUERY_ALIAS,
                query.args()
        );
    }

    private static String stripTrailingOrderBy(String sql) {
        int orderByIndex = topLevelKeywordIndex(sql, " order by ");
        if (orderByIndex < 0) {
            return sql;
        }
        int tailIndex = firstPositive(
                topLevelKeywordIndex(sql, " limit "),
                topLevelKeywordIndex(sql, " offset ")
        );
        int orderByEnd = tailIndex < 0 || tailIndex < orderByIndex ? sql.length() : tailIndex;
        // 排序段含占位符时无法安全删除（会导致参数错位），保持原样
        if (sql.indexOf('?', orderByIndex) >= 0 && sql.indexOf('?', orderByIndex) < orderByEnd) {
            return sql;
        }
        String head = sql.substring(0, orderByIndex);
        String tail = orderByEnd >= sql.length() ? "" : sql.substring(orderByEnd);
        return head + tail;
    }

    private static boolean canUseDirectCount(String sql, int fromIndex) {
        String selectClause = sql.substring(0, fromIndex).toLowerCase();
        return topLevelKeywordIndex(sql, " group by ") < 0
                && topLevelKeywordIndex(sql, " having ") < 0
                && topLevelKeywordIndex(sql, " union ") < 0
                && topLevelKeywordIndex(sql, " union all ") < 0
                && !selectClause.contains(" distinct ");
    }

    private static int topLevelKeywordIndex(String sql, String keyword) {
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

    private static int firstPositive(int... indexes) {
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

    private static String normalizeSql(String sql) {
        return sql == null ? "" : sql.trim().replaceAll("\\s+", " ");
    }

    private static Object[] trimArgsForCount(String countSql, Object[] args) {
        Object[] source = args == null ? new Object[0] : args;
        int expectedArgs = placeholderCount(countSql);
        if (expectedArgs >= source.length) {
            return source;
        }
        Object[] trimmed = new Object[expectedArgs];
        System.arraycopy(source, 0, trimmed, 0, expectedArgs);
        return trimmed;
    }

    private static int placeholderCount(String sql) {
        int count = 0;
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                count++;
            }
        }
        return count;
    }
}
