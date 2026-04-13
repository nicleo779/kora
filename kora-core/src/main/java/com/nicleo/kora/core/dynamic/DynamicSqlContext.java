package com.nicleo.kora.core.dynamic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DynamicSqlContext {
    private final Map<String, Object> bindings;
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private int uniqueNumber;

    public DynamicSqlContext(Map<String, Object> bindings) {
        this.bindings = new LinkedHashMap<>(bindings);
    }

    public Map<String, Object> getBindings() {
        return bindings;
    }

    public boolean evaluateBoolean(String expression) {
        return ExpressionEvaluator.toBoolean(evaluateValue(expression));
    }

    public Object evaluateValue(String expression) {
        return ExpressionEvaluator.evaluate(expression, this);
    }

    public Object resolveValue(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String[] parts = expression.split("\\.");
        String root = parts[0];
        for (Scope scope : scopes) {
            if (scope.values.containsKey(root)) {
                return resolvePath(scope.values.get(root), parts, 1);
            }
        }
        if (bindings.containsKey(root)) {
            return resolvePath(bindings.get(root), parts, 1);
        }
        if (bindings.containsKey("_parameter")) {
            Object parameter = bindings.get("_parameter");
            if (parameter instanceof Map<?, ?> map) {
                Object value = map.get(root);
                if (value != null || map.containsKey(root)) {
                    return resolvePath(value, parts, 1);
                }
            }
            if (isSimpleValue(parameter)) {
                return null;
            }
            return resolvePath(parameter, parts, 0);
        }
        return null;
    }

    public String applyText(String text) {
        StringBuilder sql = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int hash = text.indexOf("#{", index);
            int dollar = text.indexOf("${", index);
            int start = nextTokenStart(hash, dollar);
            if (start < 0) {
                sql.append(text.substring(index));
                break;
            }
            sql.append(text, index, start);
            boolean hashToken = start == hash;
            int end = text.indexOf('}', start + 2);
            if (end < 0) {
                throw new IllegalArgumentException("Unclosed placeholder in sql segment: " + text);
            }
            String expression = text.substring(start + 2, end).trim();
            if (hashToken) {
                sql.append("#{").append(aliasExpression(expression)).append('}');
            } else {
                Object value = evaluateValue(expression);
                sql.append(value == null ? "" : value);
            }
            index = end + 1;
        }
        return sql.toString();
    }

    public void bind(String name, Object value) {
        bindings.put(name, value);
        if (!scopes.isEmpty()) {
            scopes.peek().values.put(name, value);
        }
    }

    public int nextUniqueNumber() {
        return uniqueNumber++;
    }

    public void pushScope(Scope scope) {
        scopes.push(scope);
    }

    public void popScope() {
        scopes.pop();
    }

    private Object resolvePath(Object current, String[] parts, int startIndex) {
        Object value = current;
        for (int i = startIndex; i < parts.length; i++) {
            value = PropertyAccess.getProperty(value, parts[i]);
        }
        return value;
    }

    private String aliasExpression(String expression) {
        int dot = expression.indexOf('.');
        String root = dot < 0 ? expression : expression.substring(0, dot);
        String alias = lookupAlias(root);
        if (alias == null) {
            return expression;
        }
        return dot < 0 ? alias : alias + expression.substring(dot);
    }

    private String lookupAlias(String name) {
        for (Scope scope : scopes) {
            String alias = scope.aliases.get(name);
            if (alias != null) {
                return alias;
            }
        }
        return null;
    }

    private int nextTokenStart(int first, int second) {
        if (first < 0) {
            return second;
        }
        if (second < 0) {
            return first;
        }
        return Math.min(first, second);
    }

    private boolean isSimpleValue(Object value) {
        if (value == null) {
            return true;
        }
        Class<?> type = value.getClass();
        return type.isPrimitive()
                || value instanceof Number
                || value instanceof CharSequence
                || value instanceof Boolean
                || value instanceof Character
                || type.isEnum();
    }

    public static final class Scope {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Map<String, String> aliases = new LinkedHashMap<>();

        public void add(String name, Object value, String alias) {
            values.put(name, value);
            aliases.put(name, alias);
        }
    }
}
