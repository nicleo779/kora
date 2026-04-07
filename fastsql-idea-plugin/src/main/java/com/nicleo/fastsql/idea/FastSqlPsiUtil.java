package com.nicleo.fastsql.idea;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.nicleo.fastsql.core.TemplateSupport;

import java.util.ArrayList;
import java.util.List;

final class FastSqlPsiUtil {
    private FastSqlPsiUtil() {
    }

    static PsiLiteralExpression getFastSqlLiteral(PsiElement element) {
        PsiLiteralExpression literal = element instanceof PsiLiteralExpression psiLiteral ? psiLiteral : null;
        if (literal == null && element != null) {
            literal = PsiTreeUtil.getParentOfType(element, PsiLiteralExpression.class, false);
        }
        if (literal == null || !(literal.getValue() instanceof String)) {
            return null;
        }
        String text = getTemplateText(literal);
        if (TemplateSupport.isTemplateEditingCandidate(text)) {
            return literal;
        }
        PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(literal, PsiLocalVariable.class, false);
        if (localVariable != null && localVariable.getInitializer() == literal) {
            String name = localVariable.getName();
            if (name != null && name.toLowerCase().endsWith("sql")) {
                return literal;
            }
        }
        return null;
    }

    static String getTemplateText(PsiLiteralExpression literal) {
        TextRange valueRange = getTemplateValueRange(literal);
        String literalText = literal.getText();
        if (literalText == null) {
            return null;
        }
        int start = Math.max(0, Math.min(valueRange.getStartOffset(), literalText.length()));
        int end = Math.max(start, Math.min(valueRange.getEndOffset(), literalText.length()));
        return literalText.substring(start, end);
    }

    static TextRange getTemplateValueRange(PsiLiteralExpression literal) {
        return ElementManipulators.getValueTextRange(literal);
    }

    static TemplateContext resolveContext(CompletionParameters parameters) {
        PsiElement originalPosition = parameters.getOriginalPosition();
        PsiLiteralExpression literal = getFastSqlLiteral(originalPosition != null ? originalPosition : parameters.getPosition());
        if (literal == null) {
            return null;
        }
        TextRange valueRange = getTemplateValueRange(literal);
        int hostOffset = parameters.getOffset();
        if (!valueRange.containsOffset(hostOffset) && valueRange.containsOffset(hostOffset - 1)) {
            hostOffset--;
        }
        if (!valueRange.containsOffset(hostOffset)) {
            return null;
        }
        int templateOffset = hostOffset - valueRange.getStartOffset();
        return TemplateContext.at(literal, templateOffset);
    }

    static List<PsiVariable> visibleVariables(PsiElement context) {
        List<PsiVariable> variables = new ArrayList<>();
        PsiScopeProcessor processor = new BaseScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (element instanceof PsiVariable variable) {
                    variables.add(variable);
                }
                return true;
            }
        };
        PsiTreeUtil.treeWalkUp(processor, context, null, ResolveState.initial());
        return variables;
    }
}
