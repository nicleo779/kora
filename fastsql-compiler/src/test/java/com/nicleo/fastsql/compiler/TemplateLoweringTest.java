package com.nicleo.fastsql.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TemplateLoweringTest {
    @Test
    void lowersLoopAndCondition() {
        String code = TemplateLowering.toSupplierInvocation("""
                select * from user where id in (
                  @for (var i = 0; i < list.size(); i++) {
                    ${list.get(i)}
                    @if (i != list.size() - 1) {,}
                  }
                )
                """);

        assertTrue(code.contains("for (var i = 0; i < list.size(); i++)"));
        assertTrue(code.contains("__sb.append(java.lang.String.valueOf(list.get(i)))"));
        assertTrue(code.contains("if (i != list.size() - 1)"));
    }
}
