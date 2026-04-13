package com.nicleo.kora.core;

import com.nicleo.kora.core.xml.MapperXmlDefinition;
import com.nicleo.kora.core.xml.MapperXmlParser;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MapperXmlParserTest {
    @Test
    void parsesMapperNamespaceAndStatements() {
        String xml = """
                <mapper namespace=\"com.demo.UserMapper\">
                    <select id=\"findById\" resultType=\"com.demo.User\">
                        select * from user where id = #{id}
                    </select>
                    <insert id=\"save\">
                        insert into user(name) values(#{user.name})
                    </insert>
                </mapper>
                """;

        MapperXmlDefinition definition = MapperXmlParser.parse(new StringReader(xml));

        assertEquals("com.demo.UserMapper", definition.getNamespace());
        assertEquals(2, definition.getStatements().size());
        assertEquals("com.demo.User", definition.getStatements().get("findById").resultType());
        assertNotNull(definition.getStatements().get("findById").rootSqlNode());
    }
}
