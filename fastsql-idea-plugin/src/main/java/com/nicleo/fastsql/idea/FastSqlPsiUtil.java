package com.nicleo.fastsql.idea;

import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionCodeFragment;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.ResolveState;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.nicleo.fastsql.core.TemplateSupport;

import java.util.ArrayList;
import java.util.Deque;
import java.util.ArrayDeque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FastSqlPsiUtil {
    private static final Pattern ENHANCED_FOR_HEADER = Pattern.compile("^(var|[\\w$.<>\\[\\]?]+)\\s+(\\w+)\\s*:\\s*(.+)$", Pattern.DOTALL);

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
        PsiElement position = parameters.getPosition();
        InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(position.getProject());
        PsiLanguageInjectionHost host = injectedLanguageManager.getInjectionHost(position);
        PsiLiteralExpression literal = host instanceof PsiLiteralExpression psiLiteral ? getFastSqlLiteral(psiLiteral) : null;
        if (literal == null) {
            PsiElement originalPosition = parameters.getOriginalPosition();
            literal = getFastSqlLiteral(originalPosition != null ? originalPosition : position);
        }
        if (literal == null) {
            return null;
        }
        if (host != null) {
            int injectedOffset = parameters.getOffset() - position.getContainingFile().getTextRange().getStartOffset();
            return TemplateContext.at(literal, injectedOffset);
        }
        TextRange valueRange = getTemplateValueRange(literal);
        int hostOffset = parameters.getOffset();
        int literalContentStart = literal.getTextRange().getStartOffset() + valueRange.getStartOffset();
        int templateOffset = hostOffset - literalContentStart;
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

    static PsiType resolveExpressionType(PsiLiteralExpression literal, String expressionText) {
        return resolveExpressionType(literal, expressionText, List.of());
    }

    static PsiType resolveExpressionType(PsiLiteralExpression literal, String expressionText, List<TemplateVariable> templateVariables) {
        if (expressionText == null || expressionText.isBlank()) {
            return null;
        }
        try {
            PsiElement block = createExpressionBackedCodeBlock(literal, expressionText, templateVariables);
            for (PsiVariable variable : PsiTreeUtil.collectElementsOfType(block, PsiVariable.class)) {
                if ("__fastsql_value".equals(variable.getName()) && variable.getInitializer() != null) {
                    return variable.getInitializer().getType();
                }
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    static JavaFragmentError validateJavaFragment(PsiLiteralExpression literal, JavaFragment fragment) {
        if (fragment.text().isBlank()) {
            return null;
        }
        try {
            PsiElement psiFragment = fragment.kind() == TemplateContext.Kind.JAVA_EXPRESSION
                    ? createExpressionFragment(literal, fragment.text(), List.of())
                    : createStatementFragment(literal, "for (" + fragment.text() + ") {}");
            PsiErrorElement errorElement = PsiTreeUtil.findChildOfType(psiFragment, PsiErrorElement.class);
            if (errorElement == null) {
                return null;
            }
            int fragmentOffset = errorElement.getTextRange().getStartOffset();
            int templateOffset = fragment.kind() == TemplateContext.Kind.JAVA_EXPRESSION
                    ? fragment.startOffset() + fragmentOffset
                    : fragment.startOffset() + Math.max(0, fragmentOffset - 5);
            return new JavaFragmentError(templateOffset, errorElement.getErrorDescription());
        } catch (RuntimeException ex) {
            return new JavaFragmentError(fragment.startOffset(), ex.getMessage() != null ? ex.getMessage() : "Invalid Java fragment");
        }
    }

    static List<JavaFragment> collectJavaFragments(PsiLiteralExpression literal) {
        String text = getTemplateText(literal);
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<JavaFragment> fragments = new ArrayList<>();
        int index = 0;
        while (index < text.length()) {
            if (text.startsWith("${", index)) {
                int contentStart = index + 2;
                int end = findBalancedEnd(text, contentStart - 1, '{', '}');
                if (end > contentStart) {
                    fragments.add(new JavaFragment(TemplateContext.Kind.JAVA_EXPRESSION, contentStart, text.substring(contentStart, end)));
                    index = end + 1;
                    continue;
                }
            }
            if (text.startsWith("@if", index) || text.startsWith("@for", index)) {
                int directiveLength = text.startsWith("@if", index) ? 3 : 4;
                int parenStart = skipWhitespace(text, index + directiveLength);
                if (parenStart < text.length() && text.charAt(parenStart) == '(') {
                    int contentStart = parenStart + 1;
                    int end = findBalancedEnd(text, parenStart, '(', ')');
                    if (end >= contentStart) {
                        fragments.add(new JavaFragment(TemplateContext.Kind.JAVA_STATEMENT_HEADER, contentStart, text.substring(contentStart, end)));
                        index = end + 1;
                        continue;
                    }
                }
            }
            index++;
        }
        return fragments;
    }

    static PsiSubstitutor resolveSubstitutor(PsiType type) {
        if (type == null) {
            return PsiSubstitutor.EMPTY;
        }
        return PsiUtil.resolveGenericsClassInType(type).getSubstitutor();
    }

    static List<TemplateVariable> collectActiveTemplateVariables(PsiLiteralExpression literal, int templateOffset) {
        String text = getTemplateText(literal);
        if (text == null || templateOffset < 0) {
            return List.of();
        }
        int limit = Math.min(templateOffset, text.length());
        Deque<TemplateVariable> active = new ArrayDeque<>();
        Deque<Boolean> scopeHasVariable = new ArrayDeque<>();
        int index = 0;
        while (index < limit) {
            if (text.startsWith("@for", index)) {
                int afterDirective = skipWhitespace(text, index + 4);
                if (afterDirective < text.length() && text.charAt(afterDirective) == '(') {
                    int headerEnd = findBalancedEnd(text, afterDirective, '(', ')');
                    if (headerEnd > afterDirective) {
                        String header = text.substring(afterDirective + 1, headerEnd);
                        TemplateVariable variable = resolveEnhancedForVariable(literal, header);
                        int bracePos = skipWhitespace(text, headerEnd + 1);
                        if (bracePos < text.length() && text.charAt(bracePos) == '{' && bracePos < limit) {
                            scopeHasVariable.push(variable != null);
                            if (variable != null) {
                                active.push(variable);
                            }
                            index = bracePos + 1;
                            continue;
                        }
                    }
                }
            }
            if (text.startsWith("@if", index)) {
                int afterDirective = skipWhitespace(text, index + 3);
                if (afterDirective < text.length() && text.charAt(afterDirective) == '(') {
                    int headerEnd = findBalancedEnd(text, afterDirective, '(', ')');
                    int bracePos = headerEnd >= 0 ? skipWhitespace(text, headerEnd + 1) : -1;
                    if (bracePos >= 0 && bracePos < text.length() && text.charAt(bracePos) == '{' && bracePos < limit) {
                        scopeHasVariable.push(false);
                        index = bracePos + 1;
                        continue;
                    }
                }
            }
            if (text.charAt(index) == '}' && !scopeHasVariable.isEmpty()) {
                boolean hadVariable = scopeHasVariable.pop();
                if (hadVariable && !active.isEmpty()) {
                    active.pop();
                }
            }
            index++;
        }
        return List.copyOf(active);
    }

    static Object[] resolveReferenceVariants(PsiLiteralExpression literal, String expressionText, List<TemplateVariable> templateVariables) {
        if (expressionText == null) {
            return new Object[0];
        }
        String sanitized = sanitizeExpressionForCompletion(expressionText);
        if (sanitized.isBlank()) {
            return new Object[0];
        }
        try {
            PsiElement block = createExpressionBackedCodeBlock(literal, sanitized, templateVariables);
            PsiReferenceExpression referenceExpression = findLastReferenceExpression(block);
            if (referenceExpression != null) {
                PsiReference reference = referenceExpression.getReference();
                return reference != null ? reference.getVariants() : new Object[0];
            }
            PsiJavaCodeReferenceElement javaReference = findLastJavaCodeReference(block);
            if (javaReference != null) {
                return javaReference.getVariants();
            }
        } catch (RuntimeException ignored) {
        }
        return new Object[0];
    }

    private static PsiExpressionCodeFragment createExpressionFragment(PsiLiteralExpression literal,
                                                                     String expressionText,
                                                                     List<TemplateVariable> templateVariables) {
        return JavaCodeFragmentFactory.getInstance(literal.getProject())
                .createExpressionCodeFragment(expressionText, literal, null, true);
    }

    private static PsiElement createStatementFragment(PsiLiteralExpression literal, String statementText) {
        return JavaCodeFragmentFactory.getInstance(literal.getProject())
                .createCodeBlockCodeFragment("{" + statementText + "}", literal, true);
    }

    private static int skipWhitespace(String text, int index) {
        int current = index;
        while (current < text.length() && Character.isWhitespace(text.charAt(current))) {
            current++;
        }
        return current;
    }

    private static int findBalancedEnd(String text, int openOffset, char open, char close) {
        int depth = 0;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;
        for (int i = openOffset; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\' && i + 1 < text.length()) {
                i++;
                continue;
            }
            if (!inDoubleQuote && ch == '\'') {
                inSingleQuote = !inSingleQuote;
                continue;
            }
            if (!inSingleQuote && ch == '"') {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }
            if (inSingleQuote || inDoubleQuote) {
                continue;
            }
            if (ch == open) {
                depth++;
            } else if (ch == close) {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static String sanitizeExpressionForCompletion(String expressionText) {
        String trimmed = expressionText.stripTrailing();
        if (trimmed.isBlank()) {
            return trimmed;
        }
        if (trimmed.endsWith(".")) {
            return trimmed + "__fastsql__";
        }
        return trimmed;
    }

    private static PsiElement createExpressionBackedCodeBlock(PsiLiteralExpression literal,
                                                              String expressionText,
                                                              List<TemplateVariable> templateVariables) {
        StringBuilder builder = new StringBuilder();
        for (TemplateVariable variable : templateVariables) {
            builder.append(variable.type().getCanonicalText())
                    .append(' ')
                    .append(variable.name())
                    .append(" = null;");
        }
        builder.append("Object __fastsql_value = ").append(expressionText).append(";");
        return createStatementFragment(literal, builder.toString());
    }

    private static PsiReferenceExpression findLastReferenceExpression(PsiElement scope) {
        PsiReferenceExpression[] expressions = PsiTreeUtil.getChildrenOfType(scope, PsiReferenceExpression.class);
        if (expressions == null || expressions.length == 0) {
            expressions = PsiTreeUtil.collectElementsOfType(scope, PsiReferenceExpression.class).toArray(PsiReferenceExpression[]::new);
        }
        PsiReferenceExpression last = null;
        for (PsiReferenceExpression expression : expressions) {
            if (last == null || expression.getTextRange().getEndOffset() >= last.getTextRange().getEndOffset()) {
                last = expression;
            }
        }
        return last;
    }

    private static PsiJavaCodeReferenceElement findLastJavaCodeReference(PsiElement scope) {
        PsiJavaCodeReferenceElement last = null;
        for (PsiJavaCodeReferenceElement reference : PsiTreeUtil.collectElementsOfType(scope, PsiJavaCodeReferenceElement.class)) {
            if (last == null || reference.getTextRange().getEndOffset() >= last.getTextRange().getEndOffset()) {
                last = reference;
            }
        }
        return last;
    }

    private static TemplateVariable resolveEnhancedForVariable(PsiLiteralExpression literal, String header) {
        Matcher matcher = ENHANCED_FOR_HEADER.matcher(header.trim());
        if (!matcher.matches()) {
            return null;
        }
        String declaredType = matcher.group(1).trim();
        String variableName = matcher.group(2).trim();
        String iterableExpression = matcher.group(3).trim();
        PsiType variableType = "var".equals(declaredType)
                ? inferEnhancedForVariableType(literal, iterableExpression)
                : createTypeFromText(literal, declaredType);
        return variableType != null ? new TemplateVariable(variableName, variableType) : null;
    }

    private static PsiType inferEnhancedForVariableType(PsiLiteralExpression literal, String iterableExpression) {
        PsiType iterableType = resolveExpressionType(literal, iterableExpression);
        if (iterableType == null) {
            return null;
        }
        if (iterableType instanceof PsiArrayType arrayType) {
            return arrayType.getComponentType();
        }
        PsiType type = PsiUtil.substituteTypeParameter(iterableType, CommonClassNames.JAVA_LANG_ITERABLE, 0, false);
        return type != null ? type : createTypeFromText(literal, CommonClassNames.JAVA_LANG_OBJECT);
    }

    private static PsiType createTypeFromText(PsiLiteralExpression literal, String typeText) {
        try {
            return JavaPsiFacade.getElementFactory(literal.getProject()).createTypeFromText(typeText, literal);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    record JavaFragmentError(int templateOffset, String message) {
    }

    record JavaFragment(TemplateContext.Kind kind, int startOffset, String text) {
    }

    record TemplateVariable(String name, PsiType type) {
    }
}
