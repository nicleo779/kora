package com.nicleo.kora.core.xml;

import com.nicleo.kora.core.dynamic.BindSqlNode;
import com.nicleo.kora.core.dynamic.ChooseSqlNode;
import com.nicleo.kora.core.dynamic.DynamicSqlNode;
import com.nicleo.kora.core.dynamic.ForEachSqlNode;
import com.nicleo.kora.core.dynamic.IfSqlNode;
import com.nicleo.kora.core.dynamic.SqlNodes;
import com.nicleo.kora.core.dynamic.TrimSqlNode;
import com.nicleo.kora.core.dynamic.WhenSqlNode;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MapperXmlParser {
    private MapperXmlParser() {
    }

    public static MapperXmlDefinition parse(Reader reader) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setIgnoringComments(true);
            Document document = factory.newDocumentBuilder().parse(new InputSource(reader));
            Element root = document.getDocumentElement();
            if (root == null || !"mapper".equals(root.getTagName())) {
                throw new XmlParseException("Root element must be <mapper>");
            }
            String namespace = requiredAttribute(root, "namespace");
            Map<String, SqlNodeDefinition> statements = new LinkedHashMap<>();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) node;
                SqlCommandType commandType = SqlCommandType.fromElementName(element.getTagName());
                String id = requiredAttribute(element, "id");
                String resultType = element.getAttribute("resultType");
                String parameterType = element.getAttribute("parameterType");
                DynamicSqlNode rootSqlNode = parseChildren(element);
                statements.put(id, new SqlNodeDefinition(id, commandType, emptyToNull(resultType), emptyToNull(parameterType), rootSqlNode));
            }
            return new MapperXmlDefinition(namespace, statements);
        } catch (XmlParseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new XmlParseException("Failed to parse mapper xml", ex);
        }
    }

    private static String requiredAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isBlank()) {
            throw new XmlParseException("Missing attribute '" + name + "' on <" + element.getTagName() + ">");
        }
        return value.trim();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static DynamicSqlNode parseChildren(Element parent) {
        List<DynamicSqlNode> nodes = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            switch (node.getNodeType()) {
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    String text = node.getTextContent();
                    if (text != null && !text.isEmpty()) {
                        nodes.add(SqlNodes.text(text));
                    }
                }
                case Node.ELEMENT_NODE -> nodes.add(parseDynamicNode((Element) node));
                default -> {
                }
            }
        }
        return SqlNodes.mixed(nodes.toArray(DynamicSqlNode[]::new));
    }

    private static DynamicSqlNode parseDynamicNode(Element element) {
        return switch (element.getTagName()) {
            case "if" -> new IfSqlNode(requiredAttribute(element, "test"), parseChildren(element));
            case "trim" -> new TrimSqlNode(
                    emptyToNull(element.getAttribute("prefix")),
                    emptyToNull(element.getAttribute("suffix")),
                    splitOverrides(element.getAttribute("prefixOverrides")),
                    splitOverrides(element.getAttribute("suffixOverrides")),
                    parseChildren(element)
            );
            case "where" -> new TrimSqlNode("WHERE", null, List.of("AND", "OR"), List.of(), parseChildren(element));
            case "set" -> new TrimSqlNode("SET", null, List.of(), List.of(","), parseChildren(element));
            case "foreach" -> new ForEachSqlNode(
                    requiredAttribute(element, "collection"),
                    emptyToNull(element.getAttribute("item")),
                    emptyToNull(element.getAttribute("index")),
                    emptyToNull(element.getAttribute("open")),
                    emptyToNull(element.getAttribute("close")),
                    emptyToNull(element.getAttribute("separator")),
                    parseChildren(element)
            );
            case "choose" -> parseChoose(element);
            case "bind" -> new BindSqlNode(requiredAttribute(element, "name"), requiredAttribute(element, "value"));
            default -> throw new XmlParseException("Unsupported dynamic tag <" + element.getTagName() + ">");
        };
    }

    private static DynamicSqlNode parseChoose(Element chooseElement) {
        List<WhenSqlNode> whenNodes = new ArrayList<>();
        DynamicSqlNode otherwiseNode = null;
        NodeList children = chooseElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element element = (Element) child;
            switch (element.getTagName()) {
                case "when" -> whenNodes.add(new WhenSqlNode(requiredAttribute(element, "test"), parseChildren(element)));
                case "otherwise" -> otherwiseNode = parseChildren(element);
                default -> throw new XmlParseException("Unsupported choose child <" + element.getTagName() + ">");
            }
        }
        return new ChooseSqlNode(whenNodes, otherwiseNode);
    }

    private static List<String> splitOverrides(String value) {
        String trimmed = emptyToNull(value);
        if (trimmed == null) {
            return List.of();
        }
        String[] parts = trimmed.split("\\|");
        List<String> overrides = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                overrides.add(part.trim());
            }
        }
        return overrides;
    }
}
