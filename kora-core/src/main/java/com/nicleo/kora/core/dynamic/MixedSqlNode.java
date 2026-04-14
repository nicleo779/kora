package com.nicleo.kora.core.dynamic;

import java.util.List;

public final class MixedSqlNode implements DynamicSqlNode {
    private final List<DynamicSqlNode> children;

    public MixedSqlNode(List<DynamicSqlNode> children) {
        this.children = List.copyOf(children);
    }

    public List<DynamicSqlNode> getChildren() {
        return children;
    }

    @Override
    public String render(DynamicSqlContext context) {
        StringBuilder sql = new StringBuilder();
        for (DynamicSqlNode child : children) {
            sql.append(child.render(context));
        }
        return sql.toString();
    }
}
