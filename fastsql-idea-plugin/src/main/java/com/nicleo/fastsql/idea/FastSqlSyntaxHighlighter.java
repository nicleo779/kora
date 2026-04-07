package com.nicleo.fastsql.idea;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public final class FastSqlSyntaxHighlighter extends SyntaxHighlighterBase {
    private static final TextAttributesKey[] TEXT_KEYS = new TextAttributesKey[]{
            DefaultLanguageHighlighterColors.STRING
    };
    private static final TextAttributesKey[] DIRECTIVE_KEYS = new TextAttributesKey[]{
            DefaultLanguageHighlighterColors.KEYWORD
    };
    private static final TextAttributesKey[] INTERPOLATION_KEYS = new TextAttributesKey[]{
            DefaultLanguageHighlighterColors.BRACES
    };
    private static final TextAttributesKey[] BRACE_KEYS = new TextAttributesKey[]{
            DefaultLanguageHighlighterColors.BRACES
    };
    private static final TextAttributesKey[] PAREN_KEYS = new TextAttributesKey[]{
            DefaultLanguageHighlighterColors.PARENTHESES
    };

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new FastSqlTemplateLexer();
    }

    @Override
    public @NotNull TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        if (tokenType == FastSqlTokenType.DIRECTIVE) {
            return DIRECTIVE_KEYS;
        }
        if (tokenType == FastSqlTokenType.INTERPOLATION_START) {
            return INTERPOLATION_KEYS;
        }
        if (tokenType == FastSqlTokenType.LBRACE || tokenType == FastSqlTokenType.RBRACE) {
            return BRACE_KEYS;
        }
        if (tokenType == FastSqlTokenType.LPAREN || tokenType == FastSqlTokenType.RPAREN) {
            return PAREN_KEYS;
        }
        return TEXT_KEYS;
    }
}
