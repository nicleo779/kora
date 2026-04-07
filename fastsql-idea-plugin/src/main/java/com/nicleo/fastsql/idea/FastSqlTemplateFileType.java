package com.nicleo.fastsql.idea;

import com.intellij.openapi.fileTypes.LanguageFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class FastSqlTemplateFileType extends LanguageFileType {
    public static final FastSqlTemplateFileType INSTANCE = new FastSqlTemplateFileType();

    private FastSqlTemplateFileType() {
        super(FastSqlTemplateLanguage.INSTANCE);
    }

    @Override
    public @NotNull String getName() {
        return "FastSqlTemplate";
    }

    @Override
    public @NotNull String getDescription() {
        return "FastSql injected template";
    }

    @Override
    public @NotNull String getDefaultExtension() {
        return "fastsql-template";
    }

    @Override
    public @Nullable Icon getIcon() {
        return null;
    }
}
