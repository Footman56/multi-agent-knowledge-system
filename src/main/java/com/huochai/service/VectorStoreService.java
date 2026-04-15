package com.huochai.service;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class VectorStoreService {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    public void add(String content, Map<String, Object> metadata) {
        Document doc = new Document(content, metadata);
        vectorStore.add(List.of(doc));
    }

    public List<Document> similaritySearch(String content, double threshold) {
        // 生成查询向量
        float[] embedding = embeddingModel.embed(content);

        // 使用向量数据库检索
        return vectorStore.similaritySearch(
                org.springframework.ai.vectorstore.SearchRequest.query(content)
                        .withSimilarityThreshold(threshold)
                        .withTopK(5)
        );
    }

    public String storeLongText(String longText) {
        // 长文本分块存储
        String chunkId = UUID.randomUUID().toString();

        // 简单分块（每 1000 字符一块）
        int chunkSize = 1000;
        for (int i = 0; i < longText.length(); i += chunkSize) {
            int end = Math.min(longText.length(), i + chunkSize);
            String chunk = longText.substring(i, end);

            Map<String, Object> metadata = Map.of(
                    "chunkId", chunkId,
                    "chunkIndex", i / chunkSize,
                    "type", "long_text"
            );

            add(chunk, metadata);
        }

        return chunkId;
    }
}