package com.nicleo.kora.core.query;

import com.nicleo.kora.core.runtime.DbType;

import java.util.Collection;
import java.util.List;

public interface SqlExpression {
    void appendTo(StringBuilder sql, List<Object> args, DbType dbType);

    default NamedSqlExpression as(String alias) {
        return new AliasedExpression(this, alias);
    }

    default Order asc() {
        return new Order(this, true);
    }

    default Order desc() {
        return new Order(this, false);
    }

    default Condition eq(Object value) {
        return Conditions.eq(this, value);
    }

    default Condition eq(SqlExpression expression) {
        return Conditions.eq(this, expression);
    }

    default Condition ne(Object value) {
        return Conditions.ne(this, value);
    }

    default Condition ne(SqlExpression expression) {
        return Conditions.ne(this, expression);
    }

    default Condition gt(Object value) {
        return Conditions.gt(this, value);
    }

    default Condition gt(SqlExpression expression) {
        return Conditions.gt(this, expression);
    }

    default Condition ge(Object value) {
        return Conditions.ge(this, value);
    }

    default Condition ge(SqlExpression expression) {
        return Conditions.ge(this, expression);
    }

    default Condition lt(Object value) {
        return Conditions.lt(this, value);
    }

    default Condition lt(SqlExpression expression) {
        return Conditions.lt(this, expression);
    }

    default Condition le(Object value) {
        return Conditions.le(this, value);
    }

    default Condition le(SqlExpression expression) {
        return Conditions.le(this, expression);
    }

    default Condition like(String value) {
        return Conditions.like(this, value);
    }

    default Condition in(Collection<?> values) {
        return Conditions.in(this, values);
    }

    default Condition notIn(Collection<?> values) {
        return Conditions.notIn(this, values);
    }

    default Condition between(Object start, Object end) {
        return Conditions.between(this, start, end);
    }

    default Condition isNull() {
        return Conditions.isNull(this);
    }

    default Condition isNotNull() {
        return Conditions.isNotNull(this);
    }
}
