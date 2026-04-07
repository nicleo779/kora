package com.nicleo.fastsql.idea;

import com.intellij.lang.Language;

public final class FastSqlTemplateLanguage extends Language {
    public static final FastSqlTemplateLanguage INSTANCE = new FastSqlTemplateLanguage();

    private FastSqlTemplateLanguage() {
        super("FastSqlTemplate");
    }
}
