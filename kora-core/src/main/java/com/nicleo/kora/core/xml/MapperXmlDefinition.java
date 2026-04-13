package com.nicleo.kora.core.xml;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MapperXmlDefinition {
    private final String namespace;
    private final Map<String, SqlNodeDefinition> statements;

    public MapperXmlDefinition(String namespace, Map<String, SqlNodeDefinition> statements) {
        this.namespace = namespace;
        this.statements = Map.copyOf(new LinkedHashMap<>(statements));
    }

    public String getNamespace() {
        return namespace;
    }

    public Map<String, SqlNodeDefinition> getStatements() {
        return statements;
    }
}
