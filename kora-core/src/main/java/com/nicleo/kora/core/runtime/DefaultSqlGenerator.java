package com.nicleo.kora.core.runtime;

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
            for (int i = 0; i < definition.selectExpressions().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                definition.selectExpressions().get(i).appendTo(sql, args, dbType);
            }
        }
        sql.append(" FROM ").append(definition.from().tableReference());
        for (QueryJoin join : definition.joins()) {
            sql.append(' ').append(join.joinType()).append(' ').append(join.table().tableReference()).append(" ON ");
            join.on().appendTo(sql, args, dbType);
        }
        if (!definition.groupByExpressions().isEmpty()) {
            sql.append(" GROUP BY ");
            for (int i = 0; i < definition.groupByExpressions().size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                definition.groupByExpressions().get(i).appendTo(sql, args, dbType);
            }
        }
        appendWhere(sql, args, definition.where(), dbType);
        if (definition.having() != null) {
            sql.append(" HAVING ");
            definition.having().appendTo(sql, args, dbType);
        }
        appendOrder(sql, args, definition.where(), dbType);
        appendLimit(sql, args, definition.where(), dbType, false);
        return new SqlRequest(sql.toString(), args.toArray());
    }

    @Override
    public SqlRequest renderSelect(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("select * from ").append(table.tableName());
        appendWhere(sql, args, whereDefinition, dbType);
        appendOrder(sql, args, whereDefinition, dbType);
        appendLimit(sql, args, whereDefinition, dbType, false);
        return new SqlRequest(sql.toString(), args.toArray());
    }

    @Override
    public SqlRequest renderDelete(EntityTable<?> table, WhereDefinition whereDefinition, DbType dbType) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("delete from ").append(table.tableName());
        appendWhere(sql, args, whereDefinition, dbType);
        appendOrder(sql, args, whereDefinition, dbType);
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
            sql.append(assignment.column().columnName()).append(" = ");
            assignment.value().appendTo(sql, args, dbType);
        }
        appendWhere(sql, args, updateDefinition.where(), dbType);
        appendOrder(sql, args, updateDefinition.where(), dbType);
        appendLimit(sql, args, updateDefinition.where(), dbType, true);
        return new SqlRequest(sql.toString(), args.toArray());
    }

    private void appendWhere(StringBuilder sql, List<Object> args, WhereDefinition whereDefinition, DbType dbType) {
        if (whereDefinition != null && whereDefinition.condition() != null) {
            sql.append(" WHERE ");
            whereDefinition.condition().appendTo(sql, args, dbType);
        }
    }

    private void appendOrder(StringBuilder sql, List<Object> args, WhereDefinition whereDefinition, DbType dbType) {
        if (whereDefinition == null || whereDefinition.orders().isEmpty()) {
            return;
        }
        sql.append(" ORDER BY ");
        List<Order> orders = whereDefinition.orders();
        for (int i = 0; i < orders.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            orders.get(i).appendTo(sql, args, dbType);
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
