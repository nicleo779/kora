package com.nicleo.kora.processor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

final class GeneratedSupportScanner {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern CLASS_PATTERN = Pattern.compile("\\bpublic\\s+final\\s+class\\s+(\\w+)\\b");
    private static final Pattern REFLECTOR_PATTERN = Pattern.compile("implements\\s+GeneratedReflector<\\s*([\\w.$]+)\\s*>");
    private static final Pattern TABLE_PATTERN = Pattern.compile("extends\\s+EntityTable<\\s*([\\w.$]+)\\s*>");

    SupportRegistrations scan(Path supportSourcePath) throws IOException {
        Path scanRoot = resolveScanRoot(supportSourcePath);
        if (scanRoot == null || !Files.isDirectory(scanRoot)) {
            return new SupportRegistrations(List.of(), List.of());
        }

        Map<String, ReflectRegistration> reflectors = new LinkedHashMap<>();
        Map<String, TableRegistration> tables = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(scanRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isGeneratedRegistrationSource)
                    .forEach(path -> parseGeneratedSource(path, reflectors, tables));
        }
        return new SupportRegistrations(new ArrayList<>(reflectors.values()), new ArrayList<>(tables.values()));
    }

    private Path resolveScanRoot(Path supportSourcePath) {
        return supportSourcePath.getParent();
    }

    private boolean isGeneratedRegistrationSource(Path path) {
        String fileName = path.getFileName().toString();
        return fileName.endsWith("Reflector.java")
                || fileName.endsWith("Table.java")
                || fileName.endsWith(".kora-reflector");
    }

    private void parseGeneratedSource(Path path,
                                      Map<String, ReflectRegistration> reflectors,
                                      Map<String, TableRegistration> tables) {
        try {
            String source = Files.readString(path);
            if (path.getFileName().toString().endsWith(".kora-reflector")) {
                parseReflectorMetadata(source, reflectors);
                return;
            }
            String packageName = extract(source, PACKAGE_PATTERN);
            String simpleName = extract(source, CLASS_PATTERN);
            if (simpleName == null) {
                return;
            }
            String generatedTypeName = packageName == null || packageName.isBlank()
                    ? simpleName
                    : packageName + "." + simpleName;
            if (simpleName.endsWith("Reflector")) {
                String entityTypeName = extract(source, REFLECTOR_PATTERN);
                if (entityTypeName != null && !entityTypeName.isBlank()) {
                    reflectors.putIfAbsent(entityTypeName, new ReflectRegistration(entityTypeName, generatedTypeName));
                }
                return;
            }
            if (simpleName.endsWith("Table")) {
                String entityTypeName = extract(source, TABLE_PATTERN);
                if (entityTypeName != null && !entityTypeName.isBlank()) {
                    tables.putIfAbsent(entityTypeName, new TableRegistration(entityTypeName, generatedTypeName));
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void parseReflectorMetadata(String source, Map<String, ReflectRegistration> reflectors) {
        String[] lines = source.split("\\R");
        if (lines.length < 2) {
            return;
        }
        String entityTypeName = lines[0].trim();
        String reflectorTypeName = lines[1].trim();
        if (!entityTypeName.isEmpty() && !reflectorTypeName.isEmpty()) {
            reflectors.putIfAbsent(entityTypeName, new ReflectRegistration(entityTypeName, reflectorTypeName));
        }
    }

    private String extract(String source, Pattern pattern) {
        Matcher matcher = pattern.matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }

    record SupportRegistrations(List<ReflectRegistration> reflectors, List<TableRegistration> tables) {
    }

    record ReflectRegistration(String entityTypeName, String reflectorTypeName) {
    }

    record TableRegistration(String entityTypeName, String tableTypeName) {
    }
}
