package org.byteora.kyra.orm.dynamic;

import org.byteora.kyra.orm.runtime.BoundSql;
import org.byteora.kyra.orm.xml.SqlTemplateParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DynamicSqlRenderer {
    /**
     * Cache of the parsed SQL template (sql + binding expressions) for statements whose structure
     * never changes between calls. The mapper holds one node tree per statement as a static
     * singleton, so this cache is bounded by the number of static statements and lives for the
     * application lifetime. Only the binding values differ per call, so they are supplied fresh.
     */
    private static final Map<DynamicSqlNode, BoundSql> STATIC_TEMPLATE_CACHE = new ConcurrentHashMap<>();

    private DynamicSqlRenderer() {
    }

    public static BoundSql render(DynamicSqlNode root, Map<String, Object> bindings) {
        if (isStatic(root)) {
            BoundSql template = STATIC_TEMPLATE_CACHE.computeIfAbsent(root, DynamicSqlRenderer::renderTemplate);
            return BoundSql.ofTrusted(template.getSql(), template.getBindings(), bindings);
        }
        DynamicSqlContext context = new DynamicSqlContext(bindings);
        String rawSql = root.render(context);
        BoundSql parsed = SqlTemplateParser.parse(rawSql);
        return BoundSql.ofTrusted(parsed.getSql(), parsed.getBindings(), context.getBindings());
    }

    private static BoundSql renderTemplate(DynamicSqlNode root) {
        String rawSql = root.render(new DynamicSqlContext(Map.of()));
        return SqlTemplateParser.parse(rawSql);
    }

    /**
     * A node tree is static when its rendered SQL only depends on the (compile-time) text, i.e. it
     * contains no conditional/iteration nodes and no {@code ${}} substitutions. The {@code #{}}
     * placeholders are fine: their bound values are resolved per call from the supplied parameters.
     */
    private static boolean isStatic(DynamicSqlNode node) {
        if (node instanceof TextSqlNode textNode) {
            return !textNode.getText().contains("${");
        }
        if (node instanceof MixedSqlNode mixedNode) {
            for (DynamicSqlNode child : mixedNode.getChildren()) {
                if (!isStatic(child)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
