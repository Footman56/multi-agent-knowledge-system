package com.huochai.agent;


import com.huochai.domain.ToolLearningResult;
import com.huochai.service.MarkdownGeneratorService;
import com.huochai.service.PreviewService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class DocAgent extends BaseAgent {

    @Autowired
    private MarkdownGeneratorService markdownGenerator;

    @Autowired
    private PreviewService previewService;

    @Override
    protected String getAgentName() {
        return "DocAgent";
    }

    @Override
    public Map<String, Object> process(MyAgentState state) throws Exception {
        ToolLearningResult toolResult = state.getCurrentToolLearning();
        if (toolResult == null) {
            log.info("No tool learning result, skipping DocAgent");
            return Map.of("shouldContinue", true);
        }

        // 1. 生成 Markdown 文档
        String markdown = markdownGenerator.generate(toolResult);

        // 2. 创建预览（存储到 Redis，返回预览 ID）
        String previewId = previewService.createPreview(
                state.getUserId(),
                toolResult.getToolName(),
                markdown
        );

        // 3. 推送预览通知（可通过 WebSocket/SSE）
        previewService.notifyUser(state.getUserId(), previewId);

        // 4. 等待用户确认（实际通过异步回调，这里标记状态）
        // 在真实场景中，DocAgent 会在此处挂起，等待外部确认信号

        Map<String, Object> result = new HashMap<>();
        result.put("markdownPreview", markdown);
        result.put("previewId", previewId);
        result.put("userConfirmed", false); // 初始未确认
        result.put("shouldContinue", true);

        return result;
    }

    @Override
    protected void afterProcess(MyAgentState state, Map<String, Object> result) {
        // 如果用户已确认，则标记文档为已确认
        if (state.isUserConfirmed()) {
            result.put("userConfirmed", true);
            log.info("Document confirmed by user: {}", state.getPreviewId());
        }
    }
}