package com.nicleo.kora.core.xml;

import com.nicleo.kora.core.dynamic.DynamicSqlNode;

public record SqlNodeDefinition(String id, SqlCommandType commandType, String resultType, String parameterType,
                                DynamicSqlNode rootSqlNode) {
}
