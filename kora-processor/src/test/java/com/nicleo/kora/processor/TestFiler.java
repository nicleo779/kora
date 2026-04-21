package com.nicleo.kora.processor;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.FileObject;
import javax.tools.ForwardingFileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

final class TestFiler implements Filer {
    private final Path root;

    TestFiler(Path root) {
        this.root = root;
    }

    @Override
    public JavaFileObject createSourceFile(CharSequence name, Element... originatingElements) throws IOException {
        Path path = path(StandardLocation.SOURCE_OUTPUT, "", name.toString().replace('.', '/') + ".java");
        Files.createDirectories(path.getParent());
        return new TestJavaFileObject(path, JavaFileObject.Kind.SOURCE);
    }

    @Override
    public JavaFileObject createClassFile(CharSequence name, Element... originatingElements) throws IOException {
        Path path = path(StandardLocation.CLASS_OUTPUT, "", name.toString().replace('.', '/') + ".class");
        Files.createDirectories(path.getParent());
        return new TestJavaFileObject(path, JavaFileObject.Kind.CLASS);
    }

    @Override
    public FileObject createResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName, Element... originatingElements) throws IOException {
        Path path = path(location, pkg.toString(), relativeName.toString());
        Files.createDirectories(path.getParent());
        return new TestFileObject(path);
    }

    @Override
    public FileObject getResource(JavaFileManager.Location location, CharSequence pkg, CharSequence relativeName) throws IOException {
        Path path = path(location, pkg.toString(), relativeName.toString());
        if (!Files.exists(path)) {
            throw new IOException("Missing resource: " + path);
        }
        return new TestFileObject(path);
    }

    private Path path(JavaFileManager.Location location, String pkg, String relativeName) {
        String locationName = location == StandardLocation.CLASS_OUTPUT ? "class-output" : "source-output";
        Path base = root.resolve(locationName);
        if (!pkg.isBlank()) {
            base = base.resolve(pkg.replace('.', '/'));
        }
        return base.resolve(relativeName);
    }

    private static final class TestFileObject extends SimpleJavaFileObject {
        private final Path path;

        private TestFileObject(Path path) {
            super(path.toUri(), JavaFileObject.Kind.OTHER);
            this.path = path;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            return Files.readString(path);
        }

        @Override
        public java.io.InputStream openInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public java.io.OutputStream openOutputStream() throws IOException {
            Files.createDirectories(path.getParent());
            return Files.newOutputStream(path);
        }

        @Override
        public java.io.Reader openReader(boolean ignoreEncodingErrors) throws IOException {
            return Files.newBufferedReader(path);
        }

        @Override
        public java.io.Writer openWriter() throws IOException {
            Files.createDirectories(path.getParent());
            return Files.newBufferedWriter(path);
        }

        @Override
        public long getLastModified() {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ex) {
                return 0L;
            }
        }

        @Override
        public boolean delete() {
            try {
                return Files.deleteIfExists(path);
            } catch (IOException ex) {
                return false;
            }
        }
    }

    private static final class TestJavaFileObject extends SimpleJavaFileObject {
        private final Path path;

        private TestJavaFileObject(Path path, Kind kind) {
            super(path.toUri(), kind);
            this.path = path;
        }

        @Override
        public java.io.OutputStream openOutputStream() throws IOException {
            Files.createDirectories(path.getParent());
            return Files.newOutputStream(path);
        }
    }
}
