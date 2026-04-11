package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.dialect.DeleteModel;
import com.nicleo.kora.core.runtime.dialect.DeleteRenderer;
import com.nicleo.kora.core.runtime.dialect.OrderItem;
import com.nicleo.kora.core.runtime.dialect.PageClause;
import com.nicleo.kora.core.runtime.dialect.RenderContext;
import com.nicleo.kora.core.runtime.dialect.WhereClause;

import java.util.List;

public final class DeleteDefinitionRenderer implements DeleteRenderer {
    @Override
    public SqlRequest render(DeleteModel deleteModel, RenderContext context) {
        var sql = context.sql();
        sql.append("delete from ").append(deleteModel.table().tableReference(context.dialect().dbType()));
        QueryDefinitionRenderer.appendWhere(
                new WhereClause(deleteModel.where() == null ? null : deleteModel.where().condition()),
                context
        );
        QueryDefinitionRenderer.appendOrder(
                deleteModel.where() == null
                        ? List.of()
                        : deleteModel.where().orders().stream().map(order -> new OrderItem(order.expression(), order.ascending())).toList(),
                context
        );
        context.dialect().paging().render(new PageClause(
                deleteModel.where() == null ? null : deleteModel.where().offset(),
                deleteModel.where() == null ? null : deleteModel.where().limit(),
                true
        ), context);
        return context.toRequest();
    }
}
