package com.nicleo.kora.processor;

import javax.annotation.processing.Filer;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class GeneratedSupportIndexStore {
    private static final String PACKAGE_NAME = "gen";
    private static final String FILE_NAME = "kora-generated.idx";

    private final Filer filer;
    private final Map<String, ReflectRegistration> reflectors = new LinkedHashMap<>();
    private final Map<String, TableRegistration> tables = new LinkedHashMap<>();
    private boolean dirty;

    GeneratedSupportIndexStore(Filer filer) {
        this.filer = filer;
    }

    void load(Validator validator) {
        reflectors.clear();
        tables.clear();
        dirty = false;
        for (StandardLocation location : List.of(StandardLocation.CLASS_OUTPUT, StandardLocation.SOURCE_OUTPUT)) {
            try {
                FileObject fileObject = filer.getResource(location, PACKAGE_NAME, FILE_NAME);
                try (BufferedReader reader = new BufferedReader(fileObject.openReader(true))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Entry entry = parse(line);
                        if (entry == null) {
                            continue;
                        }
                        if (!validator.isValid(entry.entityTypeName(), entry.generatedTypeName())) {
                            dirty = true;
                            continue;
                        }
                        if ("REFLECTOR".equals(entry.kind())) {
                            reflectors.putIfAbsent(entry.entityTypeName(), new ReflectRegistration(entry.entityTypeName(), entry.generatedTypeName()));
                        } else if ("TABLE".equals(entry.kind())) {
                            tables.putIfAbsent(entry.entityTypeName(), new TableRegistration(entry.entityTypeName(), entry.generatedTypeName()));
                        }
                    }
                    return;
                }
            } catch (IOException ignored) {
            }
        }
    }

    boolean upsertReflector(String entityTypeName, String reflectorTypeName) {
        ReflectRegistration previous = reflectors.put(entityTypeName, new ReflectRegistration(entityTypeName, reflectorTypeName));
        if (previous != null && previous.reflectorTypeName().equals(reflectorTypeName)) {
            return false;
        }
        dirty = true;
        return true;
    }

    boolean upsertTable(String entityTypeName, String tableTypeName) {
        TableRegistration previous = tables.put(entityTypeName, new TableRegistration(entityTypeName, tableTypeName));
        if (previous != null && previous.tableTypeName().equals(tableTypeName)) {
            return false;
        }
        dirty = true;
        return true;
    }

    List<ReflectRegistration> reflectors() {
        return new ArrayList<>(reflectors.values());
    }

    List<TableRegistration> tables() {
        return new ArrayList<>(tables.values());
    }

    boolean isDirty() {
        return dirty;
    }

    void write() throws IOException {
        if (!dirty) {
            return;
        }
        FileObject fileObject = filer.createResource(StandardLocation.CLASS_OUTPUT, PACKAGE_NAME, FILE_NAME);
        try (Writer writer = fileObject.openWriter()) {
            for (ReflectRegistration reflector : reflectors.values()) {
                writer.write("REFLECTOR|");
                writer.write(reflector.entityTypeName());
                writer.write('|');
                writer.write(reflector.reflectorTypeName());
                writer.write('\n');
            }
            for (TableRegistration table : tables.values()) {
                writer.write("TABLE|");
                writer.write(table.entityTypeName());
                writer.write('|');
                writer.write(table.tableTypeName());
                writer.write('\n');
            }
        }
        dirty = false;
    }

    private Entry parse(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        String[] parts = line.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        String kind = parts[0].trim();
        String entityTypeName = parts[1].trim();
        String generatedTypeName = parts[2].trim();
        if (kind.isEmpty() || entityTypeName.isEmpty() || generatedTypeName.isEmpty()) {
            return null;
        }
        return new Entry(kind, entityTypeName, generatedTypeName);
    }

    @FunctionalInterface
    interface Validator {
        boolean isValid(String entityTypeName, String generatedTypeName);
    }

    private record Entry(String kind, String entityTypeName, String generatedTypeName) {
    }

    record ReflectRegistration(String entityTypeName, String reflectorTypeName) {
    }

    record TableRegistration(String entityTypeName, String tableTypeName) {
    }
}
