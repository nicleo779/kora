package com.nicleo.kora.core.dynamic;

import java.util.List;
import java.util.Locale;

public final class TrimSqlNode implements DynamicSqlNode {
    private final String prefix;
    private final String suffix;
    private final List<String> prefixOverrides;
    private final List<String> suffixOverrides;
    private final DynamicSqlNode contents;

    public TrimSqlNode(String prefix, String suffix, List<String> prefixOverrides, List<String> suffixOverrides, DynamicSqlNode contents) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.prefixOverrides = List.copyOf(prefixOverrides);
        this.suffixOverrides = List.copyOf(suffixOverrides);
        this.contents = contents;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public List<String> getPrefixOverrides() {
        return prefixOverrides;
    }

    public List<String> getSuffixOverrides() {
        return suffixOverrides;
    }

    public DynamicSqlNode getContents() {
        return contents;
    }

    @Override
    public String render(DynamicSqlContext context) {
        String body = contents.render(context).trim();
        if (body.isEmpty()) {
            return "";
        }
        body = removePrefixOverrides(body, prefixOverrides);
        body = removeSuffixOverrides(body, suffixOverrides);
        if (body.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) {
            sql.append(prefix.trim()).append(' ');
        }
        sql.append(body.trim());
        if (suffix != null && !suffix.isBlank()) {
            sql.append(' ').append(suffix.trim());
        }
        return sql.toString();
    }

    private String removePrefixOverrides(String body, List<String> overrides) {
        String candidate = body.trim();
        String upper = candidate.toUpperCase(Locale.ROOT);
        for (String override : overrides) {
            String token = override.trim();
            if (!token.isEmpty() && upper.startsWith(token.toUpperCase(Locale.ROOT))) {
                return candidate.substring(token.length()).trim();
            }
        }
        return candidate;
    }

    private String removeSuffixOverrides(String body, List<String> overrides) {
        String candidate = body.trim();
        String upper = candidate.toUpperCase(Locale.ROOT);
        for (String override : overrides) {
            String token = override.trim();
            if (!token.isEmpty() && upper.endsWith(token.toUpperCase(Locale.ROOT))) {
                return candidate.substring(0, candidate.length() - token.length()).trim();
            }
        }
        return candidate;
    }
}
