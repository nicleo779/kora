package com.nicleo.fastsql.idea;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class FastSqlTemplateInjector implements MultiHostInjector {
    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof PsiLiteralExpression literal)) {
            return;
        }
        literal = FastSqlPsiUtil.getFastSqlLiteral(literal);
        if (literal == null || !(literal instanceof PsiLanguageInjectionHost host)) {
            return;
        }
        registrar.startInjecting(FastSqlTemplateLanguage.INSTANCE);
        registrar.addPlace(null, null, host, FastSqlPsiUtil.getTemplateValueRange(literal));
        registrar.doneInjecting();
    }

    @Override
    public @NotNull List<Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(PsiLiteralExpression.class);
    }
}
