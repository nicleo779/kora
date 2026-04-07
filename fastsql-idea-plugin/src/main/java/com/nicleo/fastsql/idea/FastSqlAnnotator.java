package com.nicleo.fastsql.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.nicleo.fastsql.core.TemplateParseException;
import com.nicleo.fastsql.core.TemplateParser;
import org.jetbrains.annotations.NotNull;

public final class FastSqlAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiLiteralExpression literal)) {
            return;
        }
        literal = FastSqlPsiUtil.getFastSqlLiteral(literal);
        if (literal == null) {
            return;
        }
        String template = FastSqlPsiUtil.getTemplateText(literal);
        if (template == null) {
            return;
        }
        try {
            TemplateParser.parse(template);
        } catch (TemplateParseException ex) {
            TextRange valueRange = FastSqlPsiUtil.getTemplateValueRange(literal);
            TextRange literalRange = literal.getTextRange();
            int offset = Math.max(0, Math.min(ex.getOffset(), template.length()));
            int startOffset = literalRange.getStartOffset() + valueRange.getStartOffset() + offset;
            int endOffset = Math.min(
                    literalRange.getStartOffset() + valueRange.getEndOffset(),
                    startOffset + 1
            );
            TextRange range = new TextRange(startOffset, endOffset);
            holder.newAnnotation(HighlightSeverity.ERROR, ex.getMessage()).range(range).create();
        }
    }
}
