package com.nicleo.kora.quarkus.runtime;

import jakarta.enterprise.context.Dependent;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

@Dependent
class KoraSupportInstaller {
    private static final String SCAN_INDEX = "gen/kora-scan.idx";

    void installGeneratedSupport() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Set<String> supportClasses = readSupportClasses(classLoader);
        for (String supportClassName : supportClasses) {
            installSupportClass(supportClassName, classLoader);
        }
    }

    private Set<String> readSupportClasses(ClassLoader classLoader) {
        LinkedHashSet<String> supportClasses = new LinkedHashSet<>();
        try {
            Enumeration<java.net.URL> resources = classLoader.getResources(SCAN_INDEX);
            while (resources.hasMoreElements()) {
                java.net.URL resource = resources.nextElement();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String configClassName = parseConfigClassName(line);
                        if (configClassName != null) {
                            supportClasses.add(toSupportClassName(configClassName));
                        }
                    }
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read generated Kora scan index", ex);
        }
        return supportClasses;
    }

    private String parseConfigClassName(String line) {
        if (line == null || line.isBlank()) {
            return null;
        }
        int separator = line.indexOf('|');
        if (separator < 0) {
            return line.trim();
        }
        String configClassName = line.substring(0, separator).trim();
        return configClassName.isEmpty() ? null : configClassName;
    }

    private String toSupportClassName(String configClassName) {
        int lastDot = configClassName.lastIndexOf('.');
        String simpleName = lastDot >= 0 ? configClassName.substring(lastDot + 1) : configClassName;
        return "gen." + simpleName + "Generated";
    }

    private void installSupportClass(String supportClassName, ClassLoader classLoader) {
        try {
            Class<?> supportClass = Class.forName(supportClassName, true, classLoader);
            supportClass.getMethod("install").invoke(null);
        } catch (ClassNotFoundException ignored) {
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getTargetException() == null ? ex : ex.getTargetException();
            throw new IllegalStateException("Failed to install generated support: " + supportClassName, cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to install generated support: " + supportClassName, ex);
        }
    }
}
