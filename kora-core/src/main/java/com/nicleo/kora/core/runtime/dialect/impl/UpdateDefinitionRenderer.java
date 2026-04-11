package com.nicleo.kora.core.runtime.dialect.impl;

import com.nicleo.kora.core.query.UpdateAssignment;
import com.nicleo.kora.core.runtime.SqlRequest;
import com.nicleo.kora.core.runtime.dialect.OrderItem;
import com.nicleo.kora.core.runtime.dialect.PageClause;
import com.nicleo.kora.core.runtime.dialect.RenderContext;
import com.nicleo.kora.core.runtime.dialect.UpdateModel;
import com.nicleo.kora.core.runtime.dialect.UpdateRenderer;
import com.nicleo.kora.core.runtime.dialect.WhereClause;

import java.util.List;

public final class UpdateDefinitionRenderer implements UpdateRenderer {
    @Override
    public SqlRequest render(UpdateModel updateModel, RenderContext context) {
        StringBuilder sql = context.sql();
        List<Object> args = context.args();
        sql.append("update ").append(updateModel.table().tableReference(context.dialect().dbType())).append(" set ");
        for (int i = 0; i < updateModel.definition().assignments().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            UpdateAssignment assignment = updateModel.definition().assignments().get(i);
            sql.append(context.dialect().identifiers().quote(assignment.column().columnName())).append(" = ");
            assignment.value().appendTo(sql, args, context.dialect().dbType());
        }
        QueryDefinitionRenderer.appendWhere(
                new WhereClause(updateModel.definition().where() == null ? null : updateModel.definition().where().condition()),
                context
        );
        QueryDefinitionRenderer.appendOrder(
                updateModel.definition().where() == null
                        ? List.of()
                        : updateModel.definition().where().orders().stream().map(order -> new OrderItem(order.expression(), order.ascending())).toList(),
                context
        );
        context.dialect().paging().render(new PageClause(
                updateModel.definition().where() == null ? null : updateModel.definition().where().offset(),
                updateModel.definition().where() == null ? null : updateModel.definition().where().limit(),
                true
        ), context);
        return context.toRequest();
    }
}
