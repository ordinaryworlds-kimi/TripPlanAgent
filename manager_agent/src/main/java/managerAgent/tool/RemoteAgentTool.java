package managerAgent.tool;

import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.extern.slf4j.Slf4j;
import managerAgent.util.TimeoutUtil;
import utils.NacosUtil;

import java.time.Duration;
import java.util.List;

/**
 * 主管 Agent 的工具类：通过 A2A 协议远程调用子 Agent。
 * <p>
 * 子 Agent（RouteMakingAgent、TripPlannerAgent）在 Nacos 注册后，
 * 本类通过 {@link NacosAgentCardResolver} 发现其地址并发起同步调用。
 */
@Slf4j
public class RemoteAgentTool {

    /**
     * 调用路线制定子 Agent（端口 8082），规划自驾游路线、里程、路况等。
     *
     * @param taskDescription 子任务完整描述，需包含出发地、目的地、天数、路线偏好等
     * @return 子 Agent 返回的规划文本；失败时返回可读错误信息而非抛异常（超时除外）
     */
    @Tool(description = "调用路线制定 Agent，完成自驾游路线、里程、路况等规划")
    public String callRouteMakingAgent(
            @ToolParam(
                    name = "taskDescription",
                    description = "当前子任务的完整描述，需包含出发地、目的地、天数、路线偏好等")
            String taskDescription) throws NacosException {

        return callRemoteAgent("RouteMakingAgent", "路线制定", taskDescription);
    }

    /**
     * 调用行程规划子 Agent（端口 8085），规划景点、餐饮、住宿、天气等。
     *
     * @param taskDescription 子任务完整描述，需包含目的地、天数、行程偏好等
     * @return 子 Agent 返回的规划文本；失败时返回可读错误信息而非抛异常（超时除外）
     */
    @Tool(description = "调用行程规划 Agent，完成景点、餐饮、住宿、天气等行程安排")
    public String callTripPlannerAgent(
            @ToolParam(
                    name = "taskDescription",
                    description = "当前子任务的完整描述，需包含目的地、天数、行程偏好等")
            String taskDescription) throws NacosException {

        return callRemoteAgent("TripPlannerAgent", "行程规划", taskDescription);
    }

    /**
     * 通过 A2A 同步调用指定远程 Agent 的核心逻辑。
     * <ol>
     *   <li>从 Nacos 解析 Agent Card，构建 {@link A2aAgent}</li>
     *   <li>发送用户消息并阻塞等待响应（最长 5 分钟）</li>
     *   <li>超时：抛 {@link RuntimeException}，中断当前 ReAct 轮次</li>
     *   <li>网络中断：返回错误字符串，让主管 Agent 自行决定后续动作</li>
     * </ol>
     *
     * @param agentName  Nacos 中注册的 Agent 名称，如 RouteMakingAgent
     * @param agentLabel 中文展示名，用于日志与错误提示
     * @param taskDescription 传给子 Agent 的任务描述
     * @return 子 Agent 响应文本或错误提示
     */
    private String callRemoteAgent(String agentName, String agentLabel, String taskDescription)
            throws NacosException {

        String prompt = normalizePrompt(taskDescription, agentLabel);

        log.info("============");
        log.info("工具方法：{}智能体...正在调用中", agentLabel);
        log.info("子任务描述：{}", prompt);
        log.info("============");

        A2aAgent agent = A2aAgent.builder()
                .name(agentName)
                .agentCardResolver(new NacosAgentCardResolver(NacosUtil.getNacosClient()))
                .build();

        log.info("获取到的远程Agent描述：{}", agent.getDescription());

        try {
            Msg response = agent.call(toUserMessage(prompt)).block(Duration.ofMinutes(5));
            if (response == null || response.getTextContent() == null) {
                return agentLabel + " Agent 未返回有效结果";
            }
            String result = response.getTextContent();
            log.info("{} Agent 响应长度：{}", agentLabel, result.length());
            return result;
        } catch (Exception ex) {
            if (TimeoutUtil.isTimeout(ex)) {
                String message = TimeoutUtil.stepTimeoutMessage(agentLabel);
                log.error(message, ex);
                throw new RuntimeException(message, ex);
            }
            if (TimeoutUtil.isRemoteCallError(ex)) {
                String message = TimeoutUtil.remoteCallErrorMessage(agentLabel);
                log.error(message, ex);
                return message;
            }
            log.error("{} Agent 调用失败", agentLabel, ex);
            return agentLabel + " Agent 调用失败：" + ex.getMessage();
        }
    }

    /**
     * 规范化任务描述：空值时使用默认提示，否则去除首尾空白。
     *
     * @param taskDescription LLM 传入的任务描述，可能为空
     * @param agentLabel      中文环节名，用于构造默认提示
     * @return 最终发送给子 Agent 的 prompt 文本
     */
    private static String normalizePrompt(String taskDescription, String agentLabel) {
        if (taskDescription == null || taskDescription.isBlank()) {
            return "请根据当前旅游计划，完成" + agentLabel + "相关规划。";
        }
        return taskDescription.trim();
    }

    /**
     * 将纯文本包装为 AgentScope 用户消息对象。
     *
     * @param text 用户输入或任务描述
     * @return {@link MsgRole#USER} 角色的消息
     */
    private static Msg toUserMessage(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(text).build()))
                .build();
    }
}
