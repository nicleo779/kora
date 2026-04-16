package com.nicleo.kora.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;

final class GeneratedReflectorMetadataWriter {
    private final Filer filer;

    GeneratedReflectorMetadataWriter(Filer filer) {
        this.filer = filer;
    }

    void write(String entityTypeName, String reflectorTypeName, Element originatingElement) throws IOException {
        int separator = reflectorTypeName.lastIndexOf('.');
        String packageName = separator < 0 ? "" : reflectorTypeName.substring(0, separator);
        String fileName = (separator < 0 ? reflectorTypeName : reflectorTypeName.substring(separator + 1)) + ".kora-reflector";
        FileObject fileObject = filer.createResource(StandardLocation.SOURCE_OUTPUT, packageName, fileName, originatingElement);
        try (Writer writer = fileObject.openWriter()) {
            writer.write(entityTypeName);
            writer.write('\n');
            writer.write(reflectorTypeName);
            writer.write('\n');
        }
    }
}
