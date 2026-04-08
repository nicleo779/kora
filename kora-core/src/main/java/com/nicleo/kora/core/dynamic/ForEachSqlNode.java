package com.nicleo.kora.core.dynamic;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class ForEachSqlNode implements DynamicSqlNode {
    private final String collection;
    private final String item;
    private final String index;
    private final String open;
    private final String close;
    private final String separator;
    private final DynamicSqlNode contents;

    public ForEachSqlNode(String collection, String item, String index, String open, String close, String separator, DynamicSqlNode contents) {
        this.collection = collection;
        this.item = item;
        this.index = index;
        this.open = open;
        this.close = close;
        this.separator = separator;
        this.contents = contents;
    }

    public String getCollection() {
        return collection;
    }

    public String getItem() {
        return item;
    }

    public String getIndex() {
        return index;
    }

    public String getOpen() {
        return open;
    }

    public String getClose() {
        return close;
    }

    public String getSeparator() {
        return separator;
    }

    public DynamicSqlNode getContents() {
        return contents;
    }

    @Override
    public String render(DynamicSqlContext context) {
        Iterable<?> iterable = toIterable(context.resolveValue(collection));
        if (iterable == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        int ordinal = 0;
        for (Object value : iterable) {
            Object itemValue = value;
            Object indexValue = ordinal;
            if (value instanceof Map.Entry<?, ?> entry) {
                itemValue = entry.getValue();
                indexValue = entry.getKey();
            }
            int unique = context.nextUniqueNumber();
            DynamicSqlContext.Scope scope = new DynamicSqlContext.Scope();
            if (item != null && !item.isBlank()) {
                String itemAlias = "__frch_" + item + "_" + unique;
                context.bind(itemAlias, itemValue);
                scope.add(item, itemValue, itemAlias);
            }
            if (index != null && !index.isBlank()) {
                String indexAlias = "__frch_" + index + "_" + unique;
                context.bind(indexAlias, indexValue);
                scope.add(index, indexValue, indexAlias);
            }
            context.pushScope(scope);
            try {
                String fragment = contents.render(context).trim();
                if (!fragment.isEmpty()) {
                    parts.add(fragment);
                }
            } finally {
                context.popScope();
            }
            ordinal++;
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder sql = new StringBuilder();
        if (open != null) {
            sql.append(open);
        }
        Iterator<String> iterator = parts.iterator();
        while (iterator.hasNext()) {
            sql.append(iterator.next());
            if (iterator.hasNext() && separator != null) {
                sql.append(separator);
            }
        }
        if (close != null) {
            sql.append(close);
        }
        return sql.toString();
    }

    private Iterable<?> toIterable(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Iterable<?> iterable) {
            return iterable;
        }
        if (value instanceof Map<?, ?> map) {
            return map.entrySet();
        }
        if (value.getClass().isArray()) {
            List<Object> values = new ArrayList<>();
            int length = Array.getLength(value);
            for (int i = 0; i < length; i++) {
                values.add(Array.get(value, i));
            }
            return values;
        }
        throw new IllegalArgumentException("Collection expression '" + collection + "' did not resolve to Iterable, Map, or array");
    }
}
