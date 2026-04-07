package com.nicleo.fastsql.idea;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class FastSqlTokenType extends IElementType {
    public static final FastSqlTokenType TEXT = new FastSqlTokenType("TEXT");
    public static final FastSqlTokenType DIRECTIVE = new FastSqlTokenType("DIRECTIVE");
    public static final FastSqlTokenType INTERPOLATION_START = new FastSqlTokenType("INTERPOLATION_START");
    public static final FastSqlTokenType LBRACE = new FastSqlTokenType("LBRACE");
    public static final FastSqlTokenType RBRACE = new FastSqlTokenType("RBRACE");
    public static final FastSqlTokenType LPAREN = new FastSqlTokenType("LPAREN");
    public static final FastSqlTokenType RPAREN = new FastSqlTokenType("RPAREN");

    private FastSqlTokenType(@NotNull @NonNls String debugName) {
        super(debugName, FastSqlTemplateLanguage.INSTANCE);
    }
}
