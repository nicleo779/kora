package com.nicleo.fastsql.idea;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FastSqlCompletionContributor extends CompletionContributor {
    public FastSqlCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().inside(PsiLiteralExpression.class),
                new CompletionProvider<>() {
                    @Override
                    protected void addCompletions(@NotNull CompletionParameters parameters,
                                                  @NotNull ProcessingContext context,
                                                  @NotNull CompletionResultSet result) {
                        fillFastSqlCompletions(parameters, result);
                    }
                }
        );
    }

    @Override
    public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        fillFastSqlCompletions(parameters, result);
    }

    private void fillFastSqlCompletions(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        TemplateContext context = FastSqlPsiUtil.resolveContext(parameters);
        if (context == null) {
            return;
        }
        if (context.kind() == TemplateContext.Kind.TEMPLATE) {
            addTemplateKeywords(result, context);
            return;
        }

        String prefix = context.javaPrefix();
        int dot = prefix.lastIndexOf('.');
        if (dot >= 0) {
            String qualifier = prefix.substring(0, dot).trim();
            addQualifierMembers(result, context, qualifier);
            return;
        }
        for (PsiVariable variable : FastSqlPsiUtil.visibleVariables(context.literal())) {
            result.addElement(LookupElementBuilder.create(variable.getName()));
        }
        if (context.kind() == TemplateContext.Kind.JAVA_STATEMENT_HEADER) {
            result.addElement(LookupElementBuilder.create("var"));
        }
    }

    private void addTemplateKeywords(CompletionResultSet result, TemplateContext context) {
        String content = FastSqlPsiUtil.getTemplateText(context.literal());
        if (content == null || context.templateOffset() == 0 || content.charAt(context.templateOffset() - 1) != '@') {
            return;
        }
        result.addElement(LookupElementBuilder.create("if"));
        result.addElement(LookupElementBuilder.create("for"));
    }

    private void addQualifierMembers(CompletionResultSet result, TemplateContext context, String qualifier) {
        List<PsiVariable> variables = FastSqlPsiUtil.visibleVariables(context.literal());
        for (PsiVariable variable : variables) {
            if (!qualifier.equals(variable.getName())) {
                continue;
            }
            PsiType type = variable.getType();
            PsiClass psiClass = com.intellij.psi.util.PsiUtil.resolveClassInType(type);
            if (psiClass == null) {
                return;
            }
            for (PsiMethod method : psiClass.getAllMethods()) {
                if (method.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC)) {
                    continue;
                }
                result.addElement(LookupElementBuilder.create(method.getName() + "()"));
            }
            for (PsiField field : psiClass.getAllFields()) {
                if (field.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC)) {
                    continue;
                }
                result.addElement(LookupElementBuilder.create(field.getName()));
            }
            return;
        }
    }
}
