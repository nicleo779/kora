package com.nicleo.fastsql.idea;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.nicleo.fastsql.core.TemplateParseException;
import com.nicleo.fastsql.core.TemplateParser;
import org.jetbrains.annotations.NotNull;

public final class FastSqlAnnotator implements Annotator {
    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof PsiFile file) || !(file instanceof FastSqlTemplateFile)) {
            return;
        }
        String template = file.getText();
        if (template == null) {
            return;
        }
        try {
            TemplateParser.parse(template);
        } catch (TemplateParseException ex) {
            int offset = Math.max(0, Math.min(ex.getOffset(), template.length()));
            TextRange range = new TextRange(offset, Math.min(template.length(), offset + 1));
            holder.newAnnotation(HighlightSeverity.ERROR, ex.getMessage()).range(range).create();
            return;
        }

        PsiLiteralExpression literal = FastSqlPsiUtil.getFastSqlLiteral(
                InjectedLanguageManager.getInstance(file.getProject()).getInjectionHost(file)
        );
        if (literal == null) {
            return;
        }

        for (FastSqlPsiUtil.JavaFragment fragment : FastSqlPsiUtil.collectJavaFragments(literal)) {
            FastSqlPsiUtil.JavaFragmentError error = FastSqlPsiUtil.validateJavaFragment(literal, fragment);
            if (error == null) {
                continue;
            }
            int start = Math.max(0, Math.min(error.templateOffset(), template.length()));
            TextRange range = new TextRange(start, Math.min(template.length(), start + 1));
            holder.newAnnotation(HighlightSeverity.ERROR, error.message()).range(range).create();
            return;
        }
    }
}
