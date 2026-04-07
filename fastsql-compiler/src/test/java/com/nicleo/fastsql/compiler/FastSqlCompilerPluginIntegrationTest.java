package com.nicleo.fastsql.compiler;

import org.junit.jupiter.api.Test;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FastSqlCompilerPluginIntegrationTest {
    @Test
    void rewritesTemplateAtCompileTime() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path tempDir = Files.createTempDirectory("fastsql-compiler-test");
        Path outputDir = Files.createDirectories(tempDir.resolve("classes"));

        String source = """
                import java.util.List;

                public class Sample {
                    public static String render() {
                        var list = List.of(1, 2, 3);
                        String sql = \"\"\"
                                select * from user
                                where id in (
                                  @for (var i = 0; i < list.size(); i++) {
                                    ${list.get(i)}
                                    @if (i != list.size() - 1) {,}
                                  }
                                )
                                \"\"\";
                        return sql;
                    }
                }
                """;

        JavaFileObject fileObject = new StringJavaFileObject("Sample", source);
        List<String> options = List.of(
                "-classpath", System.getProperty("java.class.path"),
                "-d", outputDir.toString(),
                "-Xplugin:FastSqlCompiler"
        );

        boolean success = compiler.getTask(null, null, null, options, null, List.of(fileObject)).call();
        assertTrue(success, "javac compilation with FastSqlCompiler plugin should succeed");

        try (URLClassLoader classLoader = new URLClassLoader(
                new URL[]{outputDir.toUri().toURL()},
                getClass().getClassLoader()
        )) {
            Class<?> sampleClass = classLoader.loadClass("Sample");
            Method render = sampleClass.getMethod("render");
            String sql = (String) render.invoke(null);
            assertTrue(sql.contains("select * from user"));
            assertTrue(sql.contains("1"));
            assertTrue(sql.contains("2"));
            assertTrue(sql.contains("3"));
            assertTrue(sql.contains(","));
            assertTrue(!sql.contains("@for"));
            assertTrue(!sql.contains("@if"));
            assertTrue(!sql.contains("${"));
        }
    }

    private static final class StringJavaFileObject extends SimpleJavaFileObject {
        private final String source;

        private StringJavaFileObject(String className, String source) {
            super(URI.create("string:///" + className + Kind.SOURCE.extension), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}
