package com.huochai.service;


import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.annotation.PostConstruct;

@Service
public class FileSystemManager {

    @Value("${agent.knowledge.base-path:./knowledge-base}")
    private String basePath;

    private Path knowledgeRoot;

    @PostConstruct
    public void init() throws IOException {
        knowledgeRoot = Paths.get(basePath);
        if (!Files.exists(knowledgeRoot)) {
            Files.createDirectories(knowledgeRoot);
        }

        // 创建标准目录结构
        String[] categories = {"Java", "SpringBoot", "Middleware", "CloudNative", "AI", "Architecture"};
        for (String cat : categories) {
            Files.createDirectories(knowledgeRoot.resolve(cat));
        }
    }

    public String save(String content, String category, String toolName, Integer week)
            throws IOException {
        // 构建文件名：工具名_周次_日期.md
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String fileName = String.format("%s_Week%d_%s.md",
                toolName.replaceAll("\\s+", ""),
                week != null ? week : 0,
                date);

        Path categoryDir = knowledgeRoot.resolve(category);
        if (!Files.exists(categoryDir)) {
            Files.createDirectories(categoryDir);
        }

        Path filePath = categoryDir.resolve(fileName);
        Files.writeString(filePath, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        return filePath.toString();
    }

    public void updateIndex(String category, String toolName, String filePath) throws IOException {
        Path indexPath = knowledgeRoot.resolve("INDEX.md");

        String entry = String.format("- [%s](%s) - %s\n",
                toolName,
                filePath.replace(knowledgeRoot.toString(), "."),
                category);

        if (Files.exists(indexPath)) {
            Files.writeString(indexPath, entry, StandardOpenOption.APPEND);
        } else {
            Files.writeString(indexPath, "# 知识库索引\n\n" + entry);
        }
    }
}
