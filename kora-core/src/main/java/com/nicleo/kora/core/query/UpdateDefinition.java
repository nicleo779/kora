package com.nicleo.kora.core.query;

import java.util.List;

public record UpdateDefinition(
        List<UpdateAssignment> assignments,
        WhereDefinition where
) {
}
