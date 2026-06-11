package org.byteora.kyra.orm.dynamic;

/**
 * A dynamic-SQL expression (the {@code test}/{@code value} of {@code <if>}, {@code <when>} and
 * {@code <bind>}) parsed once into a reusable evaluation tree. Parsing happens when the owning
 * {@link DynamicSqlNode} is built (compile time of the mapper / class init), so each query
 * execution only walks the tree instead of re-tokenizing and re-parsing the source string.
 */
public interface CompiledExpression {
    Object evaluate(DynamicSqlContext context);
}
