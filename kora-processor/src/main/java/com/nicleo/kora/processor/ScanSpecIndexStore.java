package com.nicleo.kora.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

final class ScanSpecIndexStore {
    private static final String PACKAGE_NAME = "gen";
    private static final String FILE_NAME = "kora-scan.idx";

    private final Filer filer;

    ScanSpecIndexStore(Filer filer) {
        this.filer = filer;
    }

    List<ScanConfigMetadata> read() {
        for (StandardLocation location : List.of(StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_OUTPUT)) {
            try {
                FileObject fileObject = filer.getResource(location, PACKAGE_NAME, FILE_NAME);
                try (BufferedReader reader = new BufferedReader(fileObject.openReader(true))) {
                    List<ScanConfigMetadata> metadata = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        ScanConfigMetadata parsed = parse(line);
                        if (parsed != null) {
                            metadata.add(parsed);
                        }
                    }
                    return metadata;
                }
            } catch (IOException ignored) {
            }
        }
        return List.of();
    }

    void write(List<ScanConfigMetadata> metadata, List<? extends Element> originatingElements) throws IOException {
        Element[] elements = originatingElements.stream().filter(element -> element != null).toArray(Element[]::new);
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, PACKAGE_NAME, FILE_NAME, elements);
        try (Writer writer = fileObject.openWriter()) {
            for (ScanConfigMetadata item : metadata) {
                writer.write(item.configQualifiedName());
                writer.write('|');
                writer.write(String.join(",", item.entityPackages()));
                writer.write('|');
                writer.write(String.join(",", item.mapperPackages()));
                writer.write('\n');
            }
        }
    }

    private ScanConfigMetadata parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        return new ScanConfigMetadata(
                parts[0].trim(),
                splitPackages(parts[1]),
                splitPackages(parts[2])
        );
    }

    private List<String> splitPackages(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","))
                .stream()
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    record ScanConfigMetadata(String configQualifiedName, List<String> entityPackages, List<String> mapperPackages) {
    }
}
