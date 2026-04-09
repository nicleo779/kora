package com.nicleo.kora.core.runtime;

import com.nicleo.kora.core.query.Column;
import com.nicleo.kora.core.query.EntityTable;
import com.nicleo.kora.core.query.Order;
import com.nicleo.kora.core.query.QueryDefinition;
import com.nicleo.kora.core.query.QueryJoin;
import com.nicleo.kora.core.query.UpdateAssignment;
import com.nicleo.kora.core.query.UpdateDefinition;
import com.nicleo.kora.core.query.WhereDefinition;

import java.util.ArrayList;
import java.util.List;

public class DefaultSqlGenerator implements SqlGenerator {
    @Override
    public SqlRequest renderQuery(QueryDefinition definition, DbType dbType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT ");
        if (definition.selectAll() || definition.selectExpressions().isEmpty()) {
            sql.append(definition.from().qualifier()).append(".*");
        } else {
            sql.append(String.join(", ", definition.selectExpressions()));
        }
        sql.append(" FROM ").append(definition.from().tableReference());
        for (QueryJoin join : definition.joins()) {
            sql.append(' ').append(join.joinType()).append(' ').append(join.table().tableReference()).append(" ON ");
            join.on().appendTo(sql, args);
        }
        if (!definition.groupByColumns().isEmpty()) {
            sql.append(" GROUP BY ");
            for (int i = 0; i < definition.groupByColumns().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append(definition.groupByColumns().get(i).expression());
            }
        }
        appendWhere(sql, args, definition.where());
        appendOrder(sql, definition.where());
        appendLimit(sql, args, definition.where(), dbType, false);
        return new SqlRequest(sql.toString(), args.toArray());
    }

    @Override
    public SqlRequest renderSelect(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select * from ").append(table.tableName());
        appendWhere(sql, args, whereDefinition);
        appendOrder(sql, whereDefinition);
        appendLimit(sql, args, whereDefinition, dbType, false);
        return new SqlRequest(sql.toString(), args.toArray());
    }

    @Override
    public SqlRequest renderDelete(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("delete from ").append(table.tableName());
        appendWhere(sql, args, whereDefinition);
        appendOrder(sql, whereDefinition);
        appendLimit(sql, args, whereDefinition, dbType, true);
        return new SqlRequest(sql.toString(), args.toArray());
    }

    @Override
    public SqlRequest renderUpdate(EntityTable<?> table, UpdateDefinition updateDefinition, DbType dbType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("update ").append(table.tableName()).append(" set ");
        for (int i = 0; i < updateDefinition.assignments().size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            UpdateAssignment assignment = updateDefinition.assignments().get(i);
            sql.append(assignment.columnName()).append(" = ?");
            args.add(assignment.value());
        }
        appendWhere(sql, args, updateDefinition.where());
        appendOrder(sql, updateDefinition.where());
        appendLimit(sql, args, updateDefinition.where(), dbType, true);
        return new SqlRequest(sql.toString(), args.toArray());
    }

    private void appendWhere(StringBuilder sql, List<Object> args, WhereDefinition whereDefinition) {
        if (whereDefinition != null && whereDefinition.condition() != null) {
            sql.append(" WHERE ");
            whereDefinition.condition().appendTo(sql, args);
        }
    }

    private void appendOrder(StringBuilder sql, WhereDefinition whereDefinition) {
        if (whereDefinition == null || whereDefinition.orders().isEmpty()) {
            return;
        }
        sql.append(" ORDER BY ");
        List<Order> orders = whereDefinition.orders();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            orders.get(i).appendTo(sql);
        }
    }

    private void appendLimit(StringBuilder sql, List<Object> args, WhereDefinition whereDefinition, DbType dbType, boolean dataChange) {
        if (whereDefinition == null || whereDefinition.limit() == null) {
            return;
        }
        Integer limit = whereDefinition.limit();
        Integer offset = whereDefinition.offset();
        switch (dbType) {
            case ORACLE, SQLSERVER -> {
                if (dataChange) {
                    throw new SqlSessionException("DbType " + dbType + " does not support LIMIT on update/delete");
                }
                if (sql.indexOf(" ORDER BY ") < 0) {
                    sql.append(" ORDER BY 1");
                }
                sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
                args.add(offset == null ? 0 : offset);
                args.add(limit);
            }
            default -> {
                sql.append(" LIMIT ?");
                args.add(limit);
                if (offset != null) {
                    sql.append(" OFFSET ?");
                    args.add(offset);
                }
            }
        }
    }
}
