package com.nicleo.kora.core.dynamic;

import java.util.List;

public final class ChooseSqlNode implements DynamicSqlNode {
    private final List<WhenSqlNode> whenNodes;
    private final DynamicSqlNode otherwiseNode;

    public ChooseSqlNode(List<WhenSqlNode> whenNodes, DynamicSqlNode otherwiseNode) {
        this.whenNodes = List.copyOf(whenNodes);
        this.otherwiseNode = otherwiseNode;
    }

    public List<WhenSqlNode> getWhenNodes() {
        return whenNodes;
    }

    public DynamicSqlNode getOtherwiseNode() {
        return otherwiseNode;
    }

    @Override
    public String render(DynamicSqlContext context) {
        for (WhenSqlNode whenNode : whenNodes) {
            if (context.evaluateBoolean(whenNode.getTest())) {
                return whenNode.getContents().render(context);
            }
        }
        return otherwiseNode == null ? "" : otherwiseNode.render(context);
    }
}
