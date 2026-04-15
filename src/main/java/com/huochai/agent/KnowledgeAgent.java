package com.huochai.agent;


import com.huochai.service.FileSystemManager;
import com.huochai.service.VectorStoreService;

import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class KnowledgeAgent extends BaseAgent {

    @Autowired
    private FileSystemManager fsManager;

    @Autowired
    private VectorStoreService vectorStore;

    @Override
    protected String getAgentName() {
        return "KnowledgeAgent";
    }

    @Override
    public Map<String, Object> process(MyAgentState state) throws Exception {
        // 只有用户确认后才写入知识库
        if (!state.isUserConfirmed()) {
            log.info("Document not confirmed, skipping KnowledgeAgent");
            return Map.of("shouldContinue", true);
        }

        String markdown = state.getMarkdownPreview();
        String toolName = state.getCurrentLearningTool();

        // 1. 自动分类
        String category = classifyDocument(markdown, toolName);

        // 2. 语义去重检测
        List<Document> similar = vectorStore.similaritySearch(markdown, 0.85);
        boolean duplicateDetected = false;
        String finalContent = markdown;

        if (!similar.isEmpty()) {
            duplicateDetected = true;
            log.info("Duplicate document detected, merging...");
            // 合并重复内容（保留新内容，补充旧内容中有价值的部分）
            finalContent = mergeDocuments(markdown, similar.get(0).getContent());
        }

        // 3. 写入文件系统
        String filePath = fsManager.save(
                finalContent,
                category,
                toolName,
                state.getCurrentWeek()
        );

        // 4. 更新向量索引
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("category", category);
        metadata.put("tool", toolName);
        metadata.put("week", state.getCurrentWeek());
        metadata.put("path", filePath);

        vectorStore.add(finalContent, metadata);

        // 5. 更新目录索引
        fsManager.updateIndex(category, toolName, filePath);

        Map<String, Object> result = new HashMap<>();
        result.put("knowledgePath", filePath);
        result.put("duplicateDetected", duplicateDetected);
        result.put("mergedContent", duplicateDetected ? finalContent : null);
        result.put("shouldContinue", true);

        return result;
    }

    private String classifyDocument(String content, String toolName) {
        // 基于内容分类
        if (toolName.toLowerCase().contains("redis") ||
                toolName.toLowerCase().contains("kafka") ||
                toolName.toLowerCase().contains("rabbitmq")) {
            return "Middleware";
        } else if (toolName.toLowerCase().contains("spring")) {
            return "SpringBoot";
        } else if (toolName.toLowerCase().contains("jvm") ||
                toolName.toLowerCase().contains("gc")) {
            return "Java";
        } else if (toolName.toLowerCase().contains("docker") ||
                toolName.toLowerCase().contains("k8s")) {
            return "CloudNative";
        } else if (toolName.toLowerCase().contains("ai") ||
                toolName.toLowerCase().contains("rag")) {
            return "AI";
        }
        return "General";
    }

    private String mergeDocuments(String newDoc, String existingDoc) {
        // 简单合并策略：保留新文档的主体，附加旧文档中不重复的生产问题部分
        // 实际实现可使用 LLM 进行智能合并
        StringBuilder merged = new StringBuilder(newDoc);
        merged.append("\n\n## 历史经验补充（来自知识库）\n");
        merged.append("<!-- 以下内容来自已有文档，已去重 -->\n");
        // 提取旧文档中的生产问题部分（简化实现）
        if (existingDoc.contains("## 生产问题")) {
            String productionSection = existingDoc.substring(
                    existingDoc.indexOf("## 生产问题"));
            merged.append(productionSection);
        }
        return merged.toString();
    }
}