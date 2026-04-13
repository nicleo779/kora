package com.nicleo.kora.core.query;

public interface NamedSqlExpression extends SqlExpression {
    String alias();

    SqlExpression source();

    default SqlExpression aliasRef() {
        return Expressions.aliasRef(alias());
    }

    default Order asc() {
        return aliasRef().asc();
    }

    default Order desc() {
        return aliasRef().desc();
    }

    default Condition eq(Object value) {
        return Conditions.eq(aliasRef(), value);
    }

    default Condition ne(Object value) {
        return Conditions.ne(aliasRef(), value);
    }

    default Condition gt(Object value) {
        return Conditions.gt(aliasRef(), value);
    }

    default Condition ge(Object value) {
        return Conditions.ge(aliasRef(), value);
    }

    default Condition lt(Object value) {
        return Conditions.lt(aliasRef(), value);
    }

    default Condition le(Object value) {
        return Conditions.le(aliasRef(), value);
    }

    default Condition like(String value) {
        return Conditions.like(aliasRef(), value);
    }
}
