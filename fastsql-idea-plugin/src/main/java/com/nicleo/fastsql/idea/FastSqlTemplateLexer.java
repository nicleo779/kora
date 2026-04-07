package com.nicleo.fastsql.idea;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class FastSqlTemplateLexer extends LexerBase {
    private CharSequence buffer = "";
    private int endOffset;
    private int tokenStart;
    private int tokenEnd;
    private IElementType tokenType;

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.endOffset = endOffset;
        this.tokenStart = startOffset;
        locateToken(startOffset);
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public @Nullable IElementType getTokenType() {
        return tokenType;
    }

    @Override
    public int getTokenStart() {
        return tokenStart;
    }

    @Override
    public int getTokenEnd() {
        return tokenEnd;
    }

    @Override
    public void advance() {
        locateToken(tokenEnd);
    }

    @Override
    public @NotNull CharSequence getBufferSequence() {
        return buffer;
    }

    @Override
    public int getBufferEnd() {
        return endOffset;
    }

    private void locateToken(int start) {
        if (start >= endOffset) {
            tokenStart = endOffset;
            tokenEnd = endOffset;
            tokenType = null;
            return;
        }
        tokenStart = start;
        char ch = buffer.charAt(start);

        if (startsWith(start, "${")) {
            tokenEnd = start + 2;
            tokenType = FastSqlTokenType.INTERPOLATION_START;
            return;
        }
        if (startsWith(start, "@if")) {
            tokenEnd = start + 3;
            tokenType = FastSqlTokenType.DIRECTIVE;
            return;
        }
        if (startsWith(start, "@for")) {
            tokenEnd = start + 4;
            tokenType = FastSqlTokenType.DIRECTIVE;
            return;
        }
        if (ch == '{') {
            tokenEnd = start + 1;
            tokenType = FastSqlTokenType.LBRACE;
            return;
        }
        if (ch == '}') {
            tokenEnd = start + 1;
            tokenType = FastSqlTokenType.RBRACE;
            return;
        }
        if (ch == '(') {
            tokenEnd = start + 1;
            tokenType = FastSqlTokenType.LPAREN;
            return;
        }
        if (ch == ')') {
            tokenEnd = start + 1;
            tokenType = FastSqlTokenType.RPAREN;
            return;
        }

        int index = start + 1;
        while (index < endOffset) {
            char current = buffer.charAt(index);
            if (current == '{' || current == '}' || current == '(' || current == ')' ||
                    startsWith(index, "${") || startsWith(index, "@if") || startsWith(index, "@for")) {
                break;
            }
            index++;
        }
        tokenEnd = index;
        tokenType = FastSqlTokenType.TEXT;
    }

    private boolean startsWith(int offset, String value) {
        if (offset + value.length() > endOffset) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (buffer.charAt(offset + i) != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
