package com.nicleo.fastsql.idea;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FastSqlCompletionContributor extends CompletionContributor {
    public FastSqlCompletionContributor() {
        extend(
                CompletionType.BASIC,
                PlatformPatterns.psiElement().withLanguage(FastSqlTemplateLanguage.INSTANCE),
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
        if (addJavaReferenceVariants(result, context, prefix)) {
            return;
        }
        for (FastSqlPsiUtil.TemplateVariable variable : FastSqlPsiUtil.collectActiveTemplateVariables(context.literal(), context.templateOffset())) {
            result.addElement(createTemplateVariableLookup(variable));
        }
        for (PsiVariable variable : FastSqlPsiUtil.visibleVariables(context.literal())) {
            result.addElement(createVariableLookup(variable));
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

    private boolean addJavaReferenceVariants(CompletionResultSet result, TemplateContext context, String expressionPrefix) {
        List<FastSqlPsiUtil.TemplateVariable> templateVariables =
                FastSqlPsiUtil.collectActiveTemplateVariables(context.literal(), context.templateOffset());
        int dot = expressionPrefix.lastIndexOf('.');
        if (dot >= 0) {
            String qualifier = expressionPrefix.substring(0, dot).trim();
            for (FastSqlPsiUtil.TemplateVariable variable : templateVariables) {
                if (qualifier.equals(variable.name())) {
                    PsiClass psiClass = PsiUtil.resolveClassInType(variable.type());
                    if (psiClass == null) {
                        return false;
                    }
                    PsiSubstitutor substitutor = FastSqlPsiUtil.resolveSubstitutor(variable.type());
                    for (PsiMethod method : psiClass.getAllMethods()) {
                        if (!method.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC)) {
                            result.addElement(createMethodLookup(method, substitutor));
                        }
                    }
                    for (PsiField field : psiClass.getAllFields()) {
                        if (!field.hasModifierProperty(com.intellij.psi.PsiModifier.STATIC)) {
                            result.addElement(createFieldLookup(field, substitutor));
                        }
                    }
                    return true;
                }
            }
        }
        Object[] variants = FastSqlPsiUtil.resolveReferenceVariants(context.literal(), expressionPrefix, templateVariables);
        boolean added = false;
        for (Object variant : variants) {
            LookupElement lookup = createLookupFromVariant(variant, context.literal());
            if (lookup == null) {
                continue;
            }
            result.addElement(lookup);
            added = true;
        }
        return added;
    }

    private LookupElement createLookupFromVariant(Object variant, PsiLiteralExpression literal) {
        if (variant instanceof PsiVariable variable) {
            return createVariableLookup(variable);
        }
        if (variant instanceof PsiField field) {
            PsiSubstitutor substitutor = resolveMemberSubstitutor(literal, field);
            return createFieldLookup(field, substitutor);
        }
        if (variant instanceof PsiMethod method) {
            PsiSubstitutor substitutor = resolveMemberSubstitutor(literal, method);
            return createMethodLookup(method, substitutor);
        }
        if (variant instanceof PsiClass psiClass) {
            return LookupElementBuilder.create(psiClass.getName() != null ? psiClass.getName() : psiClass.getQualifiedName())
                    .withTypeText(psiClass.getQualifiedName(), true);
        }
        if (variant instanceof String keyword) {
            return LookupElementBuilder.create(keyword);
        }
        return null;
    }

    private PsiSubstitutor resolveMemberSubstitutor(PsiLiteralExpression literal, PsiMember member) {
        PsiClass containingClass = member.getContainingClass();
        if (containingClass == null) {
            return PsiSubstitutor.EMPTY;
        }
        PsiType matchingQualifierType = findMatchingQualifierType(literal, containingClass);
        return matchingQualifierType != null ? FastSqlPsiUtil.resolveSubstitutor(matchingQualifierType) : PsiSubstitutor.EMPTY;
    }

    private PsiType findMatchingQualifierType(PsiLiteralExpression literal, PsiClass targetClass) {
        String template = FastSqlPsiUtil.getTemplateText(literal);
        if (template == null) {
            return null;
        }
        for (int i = 0; i <= template.length(); i++) {
            TemplateContext context = TemplateContext.at(literal, i);
            if (context == null || context.kind() == TemplateContext.Kind.TEMPLATE) {
                continue;
            }
            String prefix = context.javaPrefix();
            int dot = prefix.lastIndexOf('.');
            String qualifier = dot >= 0 ? prefix.substring(0, dot).trim() : prefix.trim();
            if (qualifier.isBlank()) {
                continue;
            }
            PsiType type = FastSqlPsiUtil.resolveExpressionType(
                    literal,
                    qualifier,
                    FastSqlPsiUtil.collectActiveTemplateVariables(literal, context.templateOffset())
            );
            if (type != null) {
                PsiClass psiClass = PsiUtil.resolveClassInType(type);
                if (targetClass.equals(psiClass)) {
                    return type;
                }
            }
        }
        return null;
    }

    private LookupElement createVariableLookup(PsiVariable variable) {
        return PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(variable.getName())
                        .withTypeText(variable.getType().getPresentableText(), true),
                100.0
        );
    }

    private LookupElement createTemplateVariableLookup(FastSqlPsiUtil.TemplateVariable variable) {
        return PrioritizedLookupElement.withPriority(
                LookupElementBuilder.create(variable.name())
                        .withTypeText(variable.type().getPresentableText(), true),
                120.0
        );
    }

    private LookupElement createFieldLookup(PsiField field, PsiSubstitutor substitutor) {
        PsiType substitutedType = substitutor.substitute(field.getType());
        return LookupElementBuilder.create(field.getName())
                .withTypeText((substitutedType != null ? substitutedType : field.getType()).getPresentableText(), true);
    }

    private LookupElement createMethodLookup(PsiMethod method, PsiSubstitutor substitutor) {
        PsiType returnType = method.getReturnType();
        PsiType substitutedReturnType = returnType != null ? substitutor.substitute(returnType) : null;
        LookupElementBuilder builder = LookupElementBuilder.create(method.getName())
                .withPresentableText(method.getName())
                .withTailText(buildMethodTailText(method, substitutor), true)
                .withTypeText(substitutedReturnType != null ? substitutedReturnType.getPresentableText() : "void", true)
                .withInsertHandler((context, item) -> {
                    int tailOffset = context.getTailOffset();
                    context.getDocument().insertString(tailOffset, "()");
                    context.getEditor().getCaretModel().moveToOffset(tailOffset + 1);
                });

        for (PsiParameter parameter : method.getParameterList().getParameters()) {
            builder = builder.withLookupString(parameter.getName());
        }
        return builder;
    }

    private String buildMethodTailText(PsiMethod method, PsiSubstitutor substitutor) {
        StringBuilder tail = new StringBuilder("(");
        PsiParameter[] parameters = method.getParameterList().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                tail.append(", ");
            }
            PsiParameter parameter = parameters[i];
            PsiType substitutedType = substitutor.substitute(parameter.getType());
            tail.append((substitutedType != null ? substitutedType : parameter.getType()).getPresentableText());
            if (parameter.getName() != null && !parameter.getName().isBlank()) {
                tail.append(" ").append(parameter.getName());
            }
        }
        tail.append(")");
        return tail.toString();
    }
}
