package com.nicleo.fastsql.core;

import java.util.List;

public sealed interface TemplateNode permits TemplateNode.TextNode, TemplateNode.ExprNode, TemplateNode.IfNode, TemplateNode.ForNode {
    record TextNode(String text) implements TemplateNode {
    }

    record ExprNode(String expression) implements TemplateNode {
    }

    record IfNode(String condition, List<TemplateNode> body) implements TemplateNode {
    }

    record ForNode(String header, List<TemplateNode> body) implements TemplateNode {
    }
}
