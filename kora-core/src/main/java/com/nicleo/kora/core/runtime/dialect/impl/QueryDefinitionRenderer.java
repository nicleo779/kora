package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.dialect.GroupByItem;
import com.nicleo.kora.core.runtime.dialect.HavingClause;
import com.nicleo.kora.core.runtime.dialect.JoinItem;
import com.nicleo.kora.core.runtime.dialect.OrderItem;
import com.nicleo.kora.core.runtime.dialect.PageClause;
import com.nicleo.kora.core.runtime.dialect.QueryModel;
import com.nicleo.kora.core.runtime.dialect.QueryRenderer;
import com.nicleo.kora.core.runtime.dialect.RenderContext;
import com.nicleo.kora.core.runtime.dialect.SelectItem;
import com.nicleo.kora.core.runtime.dialect.WhereClause;

import java.util.List;

public final class QueryDefinitionRenderer implements QueryRenderer {
    @Override
    public SqlRequest render(QueryModel queryModel, RenderContext context) {
        configureColumnQualification(queryModel, context);

        var definition = queryModel.definition();
        StringBuilder sql = context.sql();
        List<Object> args = context.args();
        sql.append("SELECT ");
        if (definition.selectAll() || queryModel.selectItems().isEmpty()) {
            appendSelectAll(definition.from(), queryModel.joinItems(), context);
        } else {
            for (int i = 0; i < queryModel.selectItems().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                appendSelectItem(queryModel.selectItems().get(i), context);
            }
        }
        sql.append(" FROM ").append(definition.from().tableReference(context.dialect().dbType()));
        for (JoinItem join : queryModel.joinItems()) {
            appendJoinItem(join, context);
        }
        if (!queryModel.groupByItems().isEmpty()) {
            sql.append(" GROUP BY ");
            for (int i = 0; i < queryModel.groupByItems().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                appendGroupByItem(queryModel.groupByItems().get(i), context);
            }
        }
        appendWhere(queryModel.whereClause(), context);
        appendHaving(queryModel.havingClause(), context);
        appendOrder(queryModel.orderItems(), context);
        context.dialect().paging().render(new PageClause(
                definition.where() == null ? null : definition.where().offset(),
                definition.where() == null ? null : definition.where().limit(),
                false
        ), context);
        return context.toRequest();
    }

    static void configureSingleTableQualification(EntityTable<?> table, RenderContext context) {
        context.clearColumnQualifiers();
        if (table.alias() != null && !table.alias().isBlank()) {
            context.qualify(table, table.alias());
        } else {
            context.qualify(table, null);
        }
    }

    private static void configureColumnQualification(QueryModel queryModel, RenderContext context) {
        context.clearColumnQualifiers();
        EntityTable<?> from = queryModel.definition().from();
        boolean qualifyAll = !queryModel.joinItems().isEmpty();
        if (qualifyAll || hasAlias(from)) {
            context.qualify(from, from.qualifier());
        }
        for (JoinItem joinItem : queryModel.joinItems()) {
            context.qualify(joinItem.table(), joinItem.table().qualifier());
        }
    }

    private static void appendSelectAll(EntityTable<?> table, List<JoinItem> joinItems, RenderContext context) {
        String qualifier = !joinItems.isEmpty() || hasAlias(table) ? context.dialect().identifiers().quote(table.qualifier()) : null;
        if (qualifier == null || qualifier.isBlank()) {
            context.sql().append('*');
            return;
        }
        context.sql().append(qualifier).append(".*");
    }

    private static boolean hasAlias(EntityTable<?> table) {
        return table.alias() != null && !table.alias().isBlank();
    }

    private void appendSelectItem(SelectItem selectItem, RenderContext context) {
        selectItem.expression().appendTo(context);
        if (selectItem.aliased()) {
            context.sql().append(" AS ").append(context.dialect().identifiers().quote(selectItem.alias()));
        }
    }

    private void appendGroupByItem(GroupByItem groupByItem, RenderContext context) {
        groupByItem.expression().appendTo(context);
    }

    private void appendJoinItem(JoinItem joinItem, RenderContext context) {
        context.sql()
                .append(' ')
                .append(joinItem.joinType())
                .append(' ')
                .append(joinItem.table().tableReference(context.dialect().dbType()))
                .append(" ON ");
        joinItem.on().appendTo(context);
    }

    static void appendWhere(WhereClause whereClause, RenderContext context) {
        if (whereClause != null && whereClause.present()) {
            context.sql().append(" WHERE ");
            whereClause.condition().appendTo(context);
        }
    }

    static void appendHaving(HavingClause havingClause, RenderContext context) {
        if (havingClause != null && havingClause.present()) {
            context.sql().append(" HAVING ");
            havingClause.condition().appendTo(context);
        }
    }

    static void appendOrder(List<OrderItem> orderItems, RenderContext context) {
        if (orderItems.isEmpty()) {
            return;
        }
        StringBuilder sql = context.sql();
        sql.append(" ORDER BY ");
        for (int i = 0; i < orderItems.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            OrderItem orderItem = orderItems.get(i);
            orderItem.expression().appendTo(context);
            sql.append(orderItem.ascending() ? " ASC" : " DESC");
        }
    }
}
