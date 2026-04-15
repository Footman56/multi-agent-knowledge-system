package com.huochai.config;


import com.huochai.agent.ArchitectAgent;
import com.huochai.agent.DocAgent;
import com.huochai.agent.KnowledgeAgent;
import com.huochai.agent.MyAgentState;
import com.huochai.agent.OptimizerAgent;
import com.huochai.agent.PlannerAgent;
import com.huochai.agent.ToolAgent;


import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.internal.node.Node;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.bsc.langgraph4j.GraphDefinition.END;
import static org.bsc.langgraph4j.GraphDefinition.START;


@Configuration
public class AgentGraphConfig {

    @Bean
    public StateGraph<MyAgentState> learningAgentGraph(
            ArchitectAgent architectAgent,
            PlannerAgent plannerAgent,
            ToolAgent toolAgent,
            DocAgent docAgent,
            KnowledgeAgent knowledgeAgent,
            OptimizerAgent optimizerAgent) {

        // 将 Agent 转换为 NodeAction<AgentState>
        NodeAction<MyAgentState> architectNode = architectAgent::process;
        NodeAction<MyAgentState> plannerNode = plannerAgent::process;
        NodeAction<MyAgentState> toolNode = toolAgent::process;
        NodeAction<MyAgentState> docNode = docAgent::process;
        NodeAction<MyAgentState> knowledgeNode = knowledgeAgent::process;
        NodeAction<MyAgentState> optimizerNode = optimizerAgent::process;

        // 创建状态图并添加节点
        StateGraph<MyAgentState> graph = new StateGraph<>()
                .addNode("architect", (Node.ActionFactory) architectNode)
                .addNode("planner", (Node.ActionFactory) plannerNode)
                .addNode("tool", (Node.ActionFactory) toolNode)
                .addNode("doc", (Node.ActionFactory) docNode)
                .addNode("knowledge", (Node.ActionFactory) knowledgeNode)
                .addNode("optimizer", (Node.ActionFactory) optimizerNode)
                .addEdge(START, "architect")
                .addEdge("architect", "planner")
                .addEdge("planner", "tool")
                .addEdge("tool", "doc")
                .addEdge("doc", "knowledge")
                .addEdge("knowledge", "optimizer")
                // 条件边：根据状态决定是循环还是结束
                .addConditionalEdges("optimizer",
                        state -> {
                            if (Boolean.TRUE.equals(state.getShouldContinue())
                                    && state.hasUnfinishedTasks()) {
                                return "planner";
                            }
                            return END;
                        },
                        Map.of("planner", "planner", END, END))
                .compile();

        return graph;
    }
}