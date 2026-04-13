package com.nicleo.kora.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Writer;

final class GeneratedJavaSourceWriter {
    private final Filer filer;

    GeneratedJavaSourceWriter(Filer filer) {
        this.filer = filer;
    }

    void write(String qualifiedName, Element originatingElement, String source) throws IOException {
        JavaFileObject fileObject = filer.createSourceFile(qualifiedName, originatingElement);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(source);
        }
    }
}
