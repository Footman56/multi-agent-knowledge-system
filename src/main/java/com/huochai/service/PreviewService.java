package com.huochai.service;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PreviewService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String PREVIEW_KEY_PREFIX = "doc:preview:";
    private static final long PREVIEW_EXPIRE_HOURS = 24;

    public String createPreview(String userId, String toolName, String markdown) {
        String previewId = UUID.randomUUID().toString();
        String key = PREVIEW_KEY_PREFIX + previewId;

        // 存储预览内容和元数据
        redisTemplate.opsForHash().put(key, "userId", userId);
        redisTemplate.opsForHash().put(key, "toolName", toolName);
        redisTemplate.opsForHash().put(key, "content", markdown);
        redisTemplate.opsForHash().put(key, "status", "PENDING");
        redisTemplate.opsForHash().put(key, "createdAt", String.valueOf(System.currentTimeMillis()));

        redisTemplate.expire(key, PREVIEW_EXPIRE_HOURS, TimeUnit.HOURS);

        return previewId;
    }

    public String getPreview(String previewId) {
        String key = PREVIEW_KEY_PREFIX + previewId;
        return (String) redisTemplate.opsForHash().get(key, "content");
    }

    public void confirmPreview(String previewId) {
        String key = PREVIEW_KEY_PREFIX + previewId;
        redisTemplate.opsForHash().put(key, "status", "CONFIRMED");
    }

    public void notifyUser(String userId, String previewId) {
        // 通过 WebSocket 或消息队列通知前端
        // 简化实现：打印日志
        System.out.printf("User %s: New document preview available: %s%n", userId, previewId);
    }
}