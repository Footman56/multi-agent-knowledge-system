package com.huochai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

@Service
public class CodeExecutorService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutorService.class);
    private final Path tempDir;

    public CodeExecutorService() throws IOException {
        this.tempDir = Files.createTempDirectory("agent-code-executor");
    }

    public ExecutionResult validateAndRun(String code, String toolName) {
        ExecutionResult result = new ExecutionResult();

        try {
            // 1. 语法检查（简单编译）
            boolean syntaxOk = checkSyntax(code);
            if (!syntaxOk) {
                result.setSuccess(false);
                result.setErrorMessage("Syntax error in generated code");
                return result;
            }

            // 2. 提取依赖并尝试构建（Maven）
            String pomXml = extractOrGeneratePom(code, toolName);
            Path projectDir = createTempProject(pomXml, code);

            // 3. 执行 mvn compile
            ProcessBuilder pb = new ProcessBuilder("mvn", "compile", "-q");
            pb.directory(projectDir.toFile());
            Process process = pb.start();

            String output = new String(process.getInputStream().readAllBytes());
            String error = new String(process.getErrorStream().readAllBytes());

            int exitCode = process.waitFor();

            result.setSuccess(exitCode == 0);
            result.setOutput(output);
            if (exitCode != 0) {
                result.setErrorMessage(error);
            }

        } catch (Exception e) {
            log.error("Code execution failed", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }

        return result;
    }

    private boolean checkSyntax(String code) {
        // 使用 JavaCompiler API 进行语法检查
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            log.warn("JavaCompiler not available, skipping syntax check");
            return true;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null);

        // 创建内存中的 Java 文件对象
        JavaFileObject javaFile = new SimpleJavaFileObject(
                URI.create("string:///DemoApplication.java"),
                JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return code;
            }
        };

        Iterable<? extends JavaFileObject> compilationUnits = List.of(javaFile);
        JavaCompiler.CompilationTask task = compiler.getTask(
                null, fileManager, diagnostics, null, null, compilationUnits);

        boolean success = task.call();

        for (Diagnostic<?> diagnostic : diagnostics.getDiagnostics()) {
            log.warn("Syntax issue: {}", diagnostic.getMessage(null));
        }

        return success;
    }

    private String extractOrGeneratePom(String code, String toolName) {
        // 简化：生成标准 Spring Boot 3.5.11 pom
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>com.example</groupId>
                    <artifactId>demo</artifactId>
                    <version>0.0.1-SNAPSHOT</version>
                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.11</version>
                    </parent>
                    <properties>
                        <java.version>17</java.version>
                    </properties>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                    </dependencies>
                </project>
                """;
    }

    private Path createTempProject(String pomXml, String code) throws IOException {
        Path projectDir = tempDir.resolve(UUID.randomUUID().toString());
        Files.createDirectories(projectDir);

        // 写入 pom.xml
        Files.writeString(projectDir.resolve("pom.xml"), pomXml);

        // 创建源码目录
        Path srcDir = projectDir.resolve("src/main/java/com/example/demo");
        Files.createDirectories(srcDir);

        // 写入代码文件
        Files.writeString(srcDir.resolve("DemoApplication.java"), code);

        return projectDir;
    }

    @lombok.Data
    public static class ExecutionResult {
        private boolean success;
        private String errorMessage;
        private String output;
    }
}