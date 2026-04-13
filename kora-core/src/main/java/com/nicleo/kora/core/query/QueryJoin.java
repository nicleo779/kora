package com.nicleo.kora.core.query;

public record QueryJoin(String joinType, EntityTable<?> table, Condition on) {
}
