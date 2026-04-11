package com.nicleo.kora.core.runtime.dialect;

import com.nicleo.kora.core.query.Condition;

public record HavingClause(Condition condition) {
    public boolean present() {
        return condition != null;
    }
}
