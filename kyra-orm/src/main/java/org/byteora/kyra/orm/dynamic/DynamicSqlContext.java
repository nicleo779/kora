package org.byteora.kyra.orm.dynamic;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
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
