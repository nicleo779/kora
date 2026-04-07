package com.nicleo.fastsql.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class TemplateParserTest {
    @Test
    void parsesNestedControlFlow() {
        List<TemplateNode> nodes = TemplateParser.parse("""
                select * from user
                where id in (
                  @for (var i = 0; i < list.size(); i++) {
                    ${list.get(i)}
                    @if (i != list.size() - 1) {,}
                  }
                )
                """);

        assertEquals(3, nodes.size());
        TemplateNode.ForNode forNode = assertInstanceOf(TemplateNode.ForNode.class, nodes.get(1));
        assertEquals("var i = 0; i < list.size(); i++", forNode.header());
        assertEquals(5, forNode.body().size());
        assertInstanceOf(TemplateNode.IfNode.class, forNode.body().get(3));
    }
}
