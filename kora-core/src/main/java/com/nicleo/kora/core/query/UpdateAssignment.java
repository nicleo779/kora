package com.nicleo.kora.core.query;

public record UpdateAssignment(Column<?, ?> column, SqlExpression value) {
}
