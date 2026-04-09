package com.nicleo.kora.core.query;

import java.util.List;

public interface Condition {
    void appendTo(StringBuilder sql, List<Object> args);
}
