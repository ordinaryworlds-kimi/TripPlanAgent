package tripPlannerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.card.ConfigurableAgentCard;
import io.agentscope.core.a2a.server.transport.DeploymentProperties;
import io.agentscope.core.rag.Knowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import utils.AgentUtils;
import utils.RagUtils;

/**
 * 行程规划 Agent：负责景点、餐饮、住宿等行程安排。
 * <p>
 * 以 Spring Bean 形式注册 {@link ReActAgent}，经 Nacos A2A 供主管 Agent 远程调用（端口 8085）。
 * 当 {@code RAG_ENABLED=true} 时，启动阶段挂载 SimpleKnowledge，运行时由 LLM 按需调用
 * {@code retrieve_knowledge} 检索 {@code commons/src/main/resources/knowledge/} 下的 Markdown 文档。
 */
@Component
@Slf4j
public class TripPlannerAgent {

    /**
     * 创建并注册行程规划 ReActAgent。
     * <p>
     * 基础能力来自 {@link AgentUtils#getReActAgentBuilder(String, String)}；
     * 若 RAG 开启，则额外加载知识库并启用 AGENTIC 模式（见 {@link RagUtils}）。
     *
     * @return 可供 A2A 对外提供服务的 ReActAgent 实例
     */
    @Bean
    public ReActAgent getTripPlannerAgent() {

        /* **********************
         *
         * 1.
         * AgentScope框架自带了注册中心： AgentScopeA2aServer
         *
         * 2.
         * AgentScope框架将智能体卡片注册到注册中心,有2种方案：
         * a. 通过SpringBoot, 以Bean的形式自动注入
         * b. 手动写入注册中心, 主要针对于AgentScopeA2aServer
         *
         *
         * *********************/

        ReActAgent.Builder builder = AgentUtils.getReActAgentBuilder(
                "TripPlannerAgent",
                "擅长处理景点行程规划，可检索本地旅游知识库"
        );

        // RAG：启动时建库入库；运行时由 AgentScope 内置 retrieve_knowledge 工具触发检索
        if (RagUtils.isRagEnabled()) {
            Knowledge knowledge = RagUtils.createTravelKnowledge();
            builder = RagUtils.applyAgenticRag(builder, knowledge);
            log.info("TripPlannerAgent RAG enabled (SimpleKnowledge + AGENTIC)");
        } else {
            log.info("TripPlannerAgent RAG disabled by RAG_ENABLED=false");
        }

        return builder.build();


        //=========== 手动写入注册中心，项目不用种方式 START ====

//        //行程规划Agent 智能体卡片
//        ConfigurableAgentCard agentCard =  new ConfigurableAgentCard.Builder()
//                .name("TripPlannerAgent")
//                .description("行程规划Agent")
//                .build();
//
//        //将智能体卡片写入到AgentScope自带的注册中心
//        AgentScopeA2aServer.builder(builder)
//                .agentCard(agentCard)
//                .deploymentProperties(
//                       new DeploymentProperties(
//                               "localhost",
//                               8080)
//                )
//                .build();

        //还需要AgentScopeA2aServer启动


        //======== 手动写入注册中心，项目不用种方式 END ====


    }
}
