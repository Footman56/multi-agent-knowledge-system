package com.huochai.config;


import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class AIConfig {

    // DeepSeek 配置（核心推理）
    @Value("${spring.ai.deepseek.api-key}")
    private String deepseekApiKey;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    // Qwen 配置（Agent 调度）
    @Value("${spring.ai.qwen.api-key}")
    private String qwenApiKey;

    @Value("${spring.ai.qwen.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String qwenBaseUrl;

    // Ollama 配置（本地）
    @Value("${spring.ai.ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    // ========== DeepSeek Client（主要推理） ==========
    @Bean
    @Primary
    public OpenAiApi deepseekApi() {
        return new OpenAiApi(deepseekBaseUrl, deepseekApiKey);
    }

    @Bean
    @Primary
    public OpenAiChatModel deepseekChatModel(OpenAiApi deepseekApi) {
        return new OpenAiChatModel(deepseekApi,
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .withModel("deepseek-chat")
                        .withTemperature(0.7)
                        .withMaxTokens(4096)
                        .build());
    }

    @Bean
    @Primary
    public ChatClient deepseekChatClient(OpenAiChatModel deepseekChatModel) {
        return ChatClient.builder(deepseekChatModel).build();
    }

    // ========== Qwen Client（工具调用） ==========
    @Bean
    public OpenAiApi qwenApi() {
        return new OpenAiApi(qwenBaseUrl, qwenApiKey);
    }

    @Bean
    public OpenAiChatModel qwenChatModel(@Qualifier("qwenApi") OpenAiApi qwenApi) {
        return new OpenAiChatModel(qwenApi,
                org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .withModel("qwen-max")
                        .withTemperature(0.3)
                        .withMaxTokens(4096)
                        .build());
    }

    @Bean
    public ChatClient qwenChatClient(@Qualifier("qwenChatModel") OpenAiChatModel qwenChatModel) {
        return ChatClient.builder(qwenChatModel).build();
    }

    // ========== Ollama Client（本地 Fallback） ==========
    @Bean
    public ChatClient ollamaChatClient() {
        var ollamaApi = new org.springframework.ai.ollama.api.OllamaApi(ollamaBaseUrl);
        var ollamaChatModel = new org.springframework.ai.ollama.OllamaChatModel(ollamaApi,
                org.springframework.ai.ollama.api.OllamaOptions.builder()
                        .withModel("qwen3:7b")
                        .withTemperature(0.5)
                        .build());
        return ChatClient.builder(ollamaChatModel).build();
    }

    // ========== Embedding Model（文本向量化） ==========
    @Bean
    public OpenAiEmbeddingModel embeddingModel() {
        // 使用 DeepSeek 的 embedding 接口
        var embeddingApi = new OpenAiApi(deepseekBaseUrl, deepseekApiKey);
        return new OpenAiEmbeddingModel(embeddingApi, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder()
                        .withModel("text-embedding-v3")
                        .build());
    }
}