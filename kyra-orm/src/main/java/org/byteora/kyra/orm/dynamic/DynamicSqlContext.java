package org.byteora.kyra.orm.dynamic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicSqlContext {
    /**
     * Caches the dotted-path split of binding expressions (e.g. {@code "query.minAge"} →
     * {@code ["query", "minAge"]}). Expressions come from the (compile-time) SQL templates, so the
     * key space is bounded by the number of statements. The returned arrays are treated as read-only
     * by all callers, so sharing a single instance per expression is safe.
     */
    private static final Map<String, String[]> PATH_CACHE = new ConcurrentHashMap<>();

    private Map<String, Object> bindings;
    private boolean ownsBindings;
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private int uniqueNumber;

    public DynamicSqlContext(Map<String, Object> bindings) {
        // Share the caller's (freshly built, read-only) parameter map and only copy on first
        // mutation. Statements without <foreach>/<bind> never mutate, so they avoid the copy.
        this.bindings = bindings;
        this.ownsBindings = false;
    }

    public Map<String, Object> getBindings() {
        return bindings;
    }

    public boolean evaluateBoolean(CompiledExpression expression) {
        return ExpressionEvaluator.toBoolean(expression.evaluate(this));
    }

    public Object evaluateValue(CompiledExpression expression) {
        return expression.evaluate(this);
    }

    public Object resolveValue(String expression) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        String[] parts = splitPath(expression);
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

    public Object resolveFunction(String expression, Object argument) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        int dot = expression.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        Object target = resolveValue(expression.substring(0, dot));
        String methodName = expression.substring(dot + 1);
        return PropertyAccess.invoke(target, methodName, argument);
    }

    public void bind(String name, Object value) {
        if (!ownsBindings) {
            bindings = new LinkedHashMap<>(bindings);
            ownsBindings = true;
        }
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

    static String[] splitPath(String expression) {
        return PATH_CACHE.computeIfAbsent(expression, DynamicSqlContext::computeSplitPath);
    }

    private static String[] computeSplitPath(String expression) {
        int dot = expression.indexOf('.');
        if (dot < 0) {
            return new String[]{expression};
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (dot >= 0) {
            parts.add(expression.substring(start, dot));
            start = dot + 1;
            dot = expression.indexOf('.', start);
        }
        parts.add(expression.substring(start));
        return parts.toArray(new String[0]);
    }

    private Object resolvePath(Object current, String[] parts, int startIndex) {
        Object value = current;
        for (int i = startIndex; i < parts.length; i++) {
            value = PropertyAccess.getProperty(value, parts[i]);
        }
        return value;
    }

    String aliasExpression(String expression) {
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
