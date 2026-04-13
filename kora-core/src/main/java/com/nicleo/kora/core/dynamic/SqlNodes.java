package com.nicleo.kora.core.dynamic;

import java.util.Arrays;
import java.util.List;

public final class SqlNodes {
    private SqlNodes() {
    }

    public static DynamicSqlNode text(String text) {
        return new TextSqlNode(text);
    }

    public static DynamicSqlNode mixed(DynamicSqlNode... nodes) {
        return new MixedSqlNode(Arrays.asList(nodes));
    }

    public static DynamicSqlNode ifNode(String test, DynamicSqlNode contents) {
        return new IfSqlNode(test, contents);
    }

    public static DynamicSqlNode trim(String prefix, String suffix, String[] prefixOverrides, String[] suffixOverrides, DynamicSqlNode contents) {
        List<String> prefixes = prefixOverrides == null ? List.of() : Arrays.asList(prefixOverrides);
        List<String> suffixes = suffixOverrides == null ? List.of() : Arrays.asList(suffixOverrides);
        return new TrimSqlNode(prefix, suffix, prefixes, suffixes, contents);
    }

    public static DynamicSqlNode foreach(String collection, String item, String index, String open, String close, String separator, DynamicSqlNode contents) {
        return new ForEachSqlNode(collection, item, index, open, close, separator, contents);
    }

    public static WhenSqlNode when(String test, DynamicSqlNode contents) {
        return new WhenSqlNode(test, contents);
    }

    public static DynamicSqlNode choose(WhenSqlNode[] whenNodes, DynamicSqlNode otherwiseNode) {
        return new ChooseSqlNode(Arrays.asList(whenNodes), otherwiseNode);
    }

    public static DynamicSqlNode bind(String name, String valueExpression) {
        return new BindSqlNode(name, valueExpression);
    }
}
