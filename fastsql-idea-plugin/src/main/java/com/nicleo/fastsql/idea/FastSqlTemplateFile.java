package com.nicleo.fastsql.idea;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.psi.FileViewProvider;
import org.jetbrains.annotations.NotNull;

public final class FastSqlTemplateFile extends PsiFileBase {
    public FastSqlTemplateFile(@NotNull FileViewProvider viewProvider) {
        super(viewProvider, FastSqlTemplateLanguage.INSTANCE);
    }

    @Override
    public @NotNull com.intellij.openapi.fileTypes.FileType getFileType() {
        return FastSqlTemplateFileType.INSTANCE;
    }
}
