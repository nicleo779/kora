package com.nicleo.kora.processor;

import com.nicleo.kora.core.xml.MapperXmlDefinition;
import com.nicleo.kora.core.xml.MapperXmlParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

final class MapperXmlLoader {
    MapperXmlLoader() {
    }

    Map<String, MapperXmlDefinition> load(List<String> xmlRoots) throws IOException {
        Map<String, MapperXmlDefinition> xmlDefinitions = new LinkedHashMap<>();
        for (XmlResource xmlResource : findResources(xmlRoots)) {
            MapperXmlDefinition xmlDefinition = parse(xmlResource);
            xmlDefinitions.put(xmlDefinition.getNamespace(), xmlDefinition);
        }
        return xmlDefinitions;
    }

    private MapperXmlDefinition parse(XmlResource xmlResource) {
        try (Reader reader = xmlResource.open()) {
            return MapperXmlParser.parse(reader);
        } catch (IOException ex) {
            throw new KoraProcessor.ProcessorException("Failed to read xml: " + xmlResource.description(), ex);
        }
    }

    private List<XmlResource> findResources(List<String> xmlRoots) throws IOException {
        Map<String, XmlResource> resources = new LinkedHashMap<>();
        for (String xmlRoot : xmlRoots) {
            collectFileSystemResources(xmlRoot, resources);
        }
        return new ArrayList<>(resources.values());
    }

    private void collectFileSystemResources(String xmlRoot, Map<String, XmlResource> resources) throws IOException {
        String normalizedPath = normalizePath(xmlRoot);
        if (normalizedPath.isBlank()) {
            return;
        }
        Path path = Paths.get(normalizedPath).toAbsolutePath().normalize();
        if (!Files.exists(path)) {
            throw new IOException("Mapper xml path does not exist: " + path);
        }
        if (Files.isRegularFile(path)) {
            if (!path.toString().endsWith(".xml")) {
                throw new IOException("Mapper xml path must be an .xml file or directory: " + path);
            }
            String resourceKey = path.toString();
            resources.putIfAbsent(resourceKey, new XmlResource(resourceKey, () -> Files.newBufferedReader(path)));
            return;
        }
        try (Stream<Path> stream = Files.walk(path)) {
            for (Path candidatePath : stream.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".xml"))
                    .toList()) {
                String resourceKey = candidatePath.toAbsolutePath().normalize().toString();
                resources.putIfAbsent(resourceKey, new XmlResource(resourceKey, () -> Files.newBufferedReader(candidatePath)));
            }
        }
    }

    private String normalizePath(String path) {
        return path.replace('\\', '/').trim();
    }

    @FunctionalInterface
    private interface ReaderSupplier {
        Reader open() throws IOException;
    }

    private record XmlResource(String description, ReaderSupplier readerSupplier) {
        private Reader open() throws IOException {
            return readerSupplier.open();
        }
    }
}
