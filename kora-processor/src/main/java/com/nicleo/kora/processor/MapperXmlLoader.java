package com.nicleo.kora.processor;

import com.nicleo.kora.core.xml.MapperXmlDefinition;
import com.nicleo.kora.core.xml.MapperXmlParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

final class MapperXmlLoader {
    private final String projectDir;

    MapperXmlLoader(String projectDir) {
        this.projectDir = projectDir;
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
            collectClasspathResources(xmlRoot, resources);
        }
        return new ArrayList<>(resources.values());
    }

    private void collectFileSystemResources(String xmlRoot, Map<String, XmlResource> resources) throws IOException {
        for (Path root : candidatePaths(xmlRoot)) {
            if (!Files.exists(root)) {
                continue;
            }
            if (Files.isRegularFile(root) && root.toString().endsWith(".xml")) {
                String resourceKey = root.toAbsolutePath().normalize().toString();
                resources.putIfAbsent(resourceKey, new XmlResource(resourceKey, () -> Files.newBufferedReader(root)));
                continue;
            }
            try (Stream<Path> stream = Files.walk(root)) {
                for (Path path : stream.filter(Files::isRegularFile)
                        .filter(candidate -> candidate.toString().endsWith(".xml"))
                        .toList()) {
                    String resourceKey = path.toAbsolutePath().normalize().toString();
                    resources.putIfAbsent(resourceKey, new XmlResource(resourceKey, () -> Files.newBufferedReader(path)));
                }
            }
        }
    }

    private void collectClasspathResources(String xmlRoot, Map<String, XmlResource> resources) throws IOException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = MapperXmlLoader.class.getClassLoader();
        }
        String normalizedRoot = normalizeResourcePath(xmlRoot);
        Enumeration<URL> urls = classLoader.getResources(normalizedRoot);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            String protocol = url.getProtocol();
            if ("file".equals(protocol)) {
                try {
                    Path path = Paths.get(url.toURI());
                    collectFileResource(path, resources);
                } catch (URISyntaxException ex) {
                    throw new IOException("Failed to resolve classpath xml root: " + url, ex);
                }
                continue;
            }
            if ("jar".equals(protocol)) {
                collectJarResources(url, normalizedRoot, classLoader, resources);
            }
        }
    }

    private List<Path> candidatePaths(String relativePath) {
        String normalized = normalizeResourcePath(relativePath);
        Path direct = Paths.get(normalized);
        List<Path> paths = new ArrayList<>();
        if (direct.isAbsolute()) {
            paths.add(direct);
            return paths;
        }
        paths.add(Paths.get(projectDir, normalized));
        paths.add(Paths.get(projectDir, "src/main/resources", normalized));
        paths.add(Paths.get(projectDir, "src/test/resources", normalized));
        return paths;
    }

    private void collectFileResource(Path root, Map<String, XmlResource> resources) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        if (Files.isRegularFile(root) && root.toString().endsWith(".xml")) {
            String resourceKey = root.toAbsolutePath().normalize().toString();
            resources.putIfAbsent(resourceKey, new XmlResource(resourceKey, () -> Files.newBufferedReader(root)));
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.filter(Files::isRegularFile)
                    .filter(candidate -> candidate.toString().endsWith(".xml"))
                    .toList()) {
                String resourceKey = path.toAbsolutePath().normalize().toString();
                resources.putIfAbsent(resourceKey, new XmlResource(resourceKey, () -> Files.newBufferedReader(path)));
            }
        }
    }

    private void collectJarResources(URL rootUrl, String normalizedRoot, ClassLoader classLoader, Map<String, XmlResource> resources) throws IOException {
        JarURLConnection connection = (JarURLConnection) rootUrl.openConnection();
        String entryPrefix = connection.getEntryName();
        try (JarFile jarFile = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String entryName = entry.getName();
                if (!entryName.endsWith(".xml")) {
                    continue;
                }
                if (entryPrefix != null && !entryName.startsWith(entryPrefix)) {
                    continue;
                }
                if (entryPrefix == null && !entryName.startsWith(normalizedRoot)) {
                    continue;
                }
                String resourceKey = "jar:" + jarFile.getName() + "!/" + entryName;
                resources.putIfAbsent(resourceKey, new XmlResource(resourceKey, () -> openClasspathReader(classLoader, entryName)));
            }
        }
    }

    private Reader openClasspathReader(ClassLoader classLoader, String resourceName) throws IOException {
        InputStream inputStream = classLoader.getResourceAsStream(resourceName);
        if (inputStream == null) {
            throw new IOException("Classpath resource not found: " + resourceName);
        }
        return new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    }

    private String normalizeResourcePath(String relativePath) {
        String normalized = relativePath.replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
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
