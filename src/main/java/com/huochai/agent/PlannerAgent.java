package com.huochai.agent;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huochai.domain.LearningPlan;
import com.huochai.domain.Skill;
import com.huochai.domain.Task;
import com.huochai.repository.LearningPlanRepository;
import com.huochai.repository.TaskRepository;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class PlannerAgent extends BaseAgent {

    @Autowired
    @Qualifier("deepseekChatClient")
    private ChatClient deepseekClient;

    @Autowired
    private LearningPlanRepository planRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected String getAgentName() {
        return "PlannerAgent";
    }

    @Override
    public Map<String, Object> process(MyAgentState state) throws Exception {
        // 获取未完成任务（顺延）
        List<Task> rolloverTasks = state.getUnfinishedTasks();
        if (rolloverTasks == null) {
            rolloverTasks = new ArrayList<>();
        }

        // 获取能力模型
        List<Skill> skills = state.getSkills();
        if (skills == null || skills.isEmpty()) {
            throw new IllegalStateException("Skills not provided by ArchitectAgent");
        }

        // 确定当前周数
        Integer currentWeek = state.getCurrentWeek();
        if (currentWeek == null) {
            currentWeek = getLastCompletedWeek(state.getUserId()) + 1;
        } else {
            currentWeek = currentWeek + 1; // 新的一周
        }

        // 调用 DeepSeek 生成周计划
        String prompt = buildPlannerPrompt(skills, rolloverTasks, currentWeek);

        String response = deepseekClient.prompt()
                .user(prompt)
                .call()
                .content();

        LearningPlan newPlan = parseLearningPlan(response, currentWeek);

        // 融合顺延任务
        if (!rolloverTasks.isEmpty()) {
            newPlan.getTasks().addAll(0, rolloverTasks);
        }

        // 持久化到数据库
        newPlan.setCreatedAt(new Date());
        planRepository.save(newPlan);
        taskRepository.saveAll(newPlan.getTasks());

        // 更新状态
        List<LearningPlan> allPlans = state.getLearningPlans();
        if (allPlans == null) {
            allPlans = new ArrayList<>();
        }
        allPlans.add(newPlan);

        Map<String, Object> result = new HashMap<>();
        result.put("currentWeek", currentWeek);
        result.put("learningPlans", allPlans);
        result.put("unfinishedTasks", newPlan.getTasks().stream()
                .filter(t -> !t.getDone())
                .collect(Collectors.toList()));
        result.put("shouldContinue", true);

        return result;
    }

    private String buildPlannerPrompt(List<Skill> skills, List<Task> rolloverTasks, int week) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位资深技术导师，请为高级 Java 工程师生成第 ").append(week).append(" 周学习计划。\n\n");
        sb.append("需要掌握的能力模型：\n");
        for (Skill skill : skills) {
            sb.append("- ").append(skill.getName())
                    .append(" (重要性: ").append(skill.getImportance())
                    .append(", 预计耗时: ").append(skill.getTimeEstimateHours()).append("h)\n");
        }

        if (!rolloverTasks.isEmpty()) {
            sb.append("\n上周未完成任务（需要顺延到本周）：\n");
            for (Task task : rolloverTasks) {
                sb.append("- ").append(task.getName()).append("\n");
            }
        }

        sb.append("""
                
                请生成本周的具体学习任务，要求：
                1. 每周总学习时间不超过 40 小时
                2. 优先安排 importance=high 的技能
                3. 每个任务必须包含 name, howToLearn（具体学习方法）, estimatedHours
                4. 输出严格的 JSON 格式：
                {
                  "week": %d,
                  "topics": ["主题1", "主题2"],
                  "tasks": [
                    {
                      "name": "任务名称",
                      "howToLearn": "源码阅读+动手实践",
                      "estimatedHours": 8,
                      "deliverables": ["产出物1", "产出物2"]
                    }
                  ],
                  "rollover": true
                }
                
                只输出 JSON，不要其他内容。
                """.formatted(week));

        return sb.toString();
    }

    private LearningPlan parseLearningPlan(String response, int week) throws Exception {
        String json = extractJson(response);
        JsonNode root = objectMapper.readTree(json);

        List<Task> tasks = new ArrayList<>();
        JsonNode tasksNode = root.get("tasks");
        if (tasksNode.isArray()) {
            for (JsonNode node : tasksNode) {
                Task task = Task.builder()
                        .id(UUID.randomUUID().toString())
                        .name(node.get("name").asText())
                        .howToLearn(node.get("howToLearn").asText())
                        .estimatedHours(node.get("estimatedHours").asInt())
                        .done(false)
                        .deadline(calculateDeadline(week))
                        .deliverables(objectMapper.convertValue(
                                node.get("deliverables"),
                                new TypeReference<List<String>>() {}))
                        .build();
                tasks.add(task);
            }
        }

        List<String> topics = objectMapper.convertValue(
                root.get("topics"),
                new TypeReference<List<String>>() {});

        return LearningPlan.builder()
                .week(week)
                .topics(topics)
                .tasks(tasks)
                .rollover(root.get("rollover").asBoolean())
                .build();
    }

    private String extractJson(String response) {
        if (response.contains("```json")) {
            String json = response.substring(response.indexOf("```json") + 7);
            return json.substring(0, json.indexOf("```")).trim();
        } else if (response.contains("```")) {
            String json = response.substring(response.indexOf("```") + 3);
            return json.substring(0, json.indexOf("```")).trim();
        }
        return response.trim();
    }

    private Date calculateDeadline(int week) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.WEEK_OF_YEAR, week);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.FRIDAY);
        return cal.getTime();
    }

    private Integer getLastCompletedWeek(String userId) {
        return planRepository.findMaxWeekByUserId(userId).orElse(0);
    }
}