package managerAgent.agents;

import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.plan.model.Plan;
import io.agentscope.core.plan.model.PlanState;
import io.agentscope.core.tool.Toolkit;
import managerAgent.dto.ChatEvent;
import managerAgent.hook.planHook;
import managerAgent.plan.TripPlan;
import managerAgent.tool.RemoteAgentTool;
import managerAgent.util.PlanMapper;
import managerAgent.util.TimeoutUtil;
import utils.AgentUtils;
import utils.ToolUtils;

import java.util.List;
import java.util.Scanner;
import java.util.function.Consumer;

/**
 * 主管 Agent：负责拆解旅游计划、编排子任务，并通过工具调用远程子 Agent。
 * <p>
 * 支持两种运行模式：
 * <ul>
 *   <li>控制台：{@link #run()} 循环读取 stdin</li>
 *   <li>Web：{@link #streamTurn(String)} 流式输出并通过 eventSink 推送 SSE 事件</li>
 * </ul>
 */
public class ManagerAgent {

    /** 计划处于 IN_PROGRESS 时，自动推进剩余子任务使用的固定提示 */
    public static final String AUTO_CONTINUE_PROMPT = "请继续执行计划的剩余子任务。";

    private final ReActAgent agent;
    private final PlanNotebook planNotebook;
    private final Consumer<ChatEvent> eventSink;

    /** 控制台模式：不推送 Web 事件 */
    public ManagerAgent() {
        this(null);
    }

    /**
     * @param eventSink Web 模式下接收思考/工具/消息/计划事件的回调；控制台模式传 null
     */
    public ManagerAgent(Consumer<ChatEvent> eventSink) {
        this.eventSink = eventSink;

        TripPlan plan = new TripPlan();
        ToolUtils toolUtils = new ToolUtils();
        Toolkit toolkit = toolUtils.getToolkit(new RemoteAgentTool());
        planNotebook = plan.getPlan();

        agent = AgentUtils.getReActAgentBuilder("ManagerAgent", "主管Agent")
                .planNotebook(planNotebook)
                .hook(new planHook(planNotebook, this::emit))
                .toolkit(toolkit)
                .build();
    }

    /**
     * 控制台交互主循环：流式打印 Agent 输出，计划完成后等待用户确认或修改。
     */
    public void run() {
        String prompt =
                """
                        帮我制定2027年元旦,
                        深圳到惠州3日游自驾游计划，
                        请包含吃住行，天气，酒店，餐饮美食。
                        """;

        Scanner scanner = new Scanner(System.in);
        Msg userMsg = toUserMessage(prompt);

        while (true) {
            streamTurnToConsole(userMsg);

            Plan currentPlan = planNotebook.getCurrentPlan();
            if (currentPlan == null
                    || currentPlan.getState() == PlanState.DONE
                    || currentPlan.getState() == PlanState.ABANDONED) {
                break;
            }

            if (currentPlan.getState() == PlanState.IN_PROGRESS) {
                userMsg = toUserMessage(AUTO_CONTINUE_PROMPT);
                continue;
            }

            System.out.print("请输入确认或修改意见（quit 退出）: ");
            String input = scanner.nextLine().trim();
            if (input.isEmpty() || "quit".equalsIgnoreCase(input)) {
                break;
            }
            userMsg = toUserMessage(input);
        }
    }

    /**
     * 执行单轮对话（Web 入口），将用户文本转为消息后流式处理。
     *
     * @param userText 用户输入
     * @return 本轮 Agent 回复的完整文本
     */
    public String streamTurn(String userText) {
        return streamTurn(toUserMessage(userText));
    }

    /**
     * 执行单轮对话的核心逻辑：流式调用 Agent，累积回复并推送中间事件。
     *
     * @param userMsg 用户消息对象
     * @return 本轮完整回复文本
     * @throws RuntimeException 超时时携带中文提示
     */
    public String streamTurn(Msg userMsg) {
        StringBuilder response = new StringBuilder();
        try {
            agent.stream(userMsg)
                    .doOnNext(event -> {
                        String chunk = safeText(event);
                        if (!chunk.isBlank()) {
                            response.append(chunk);
                            emit(ChatEvent.message(chunk));
                        }
                    })
                    .blockLast();
        } catch (Exception ex) {
            if (TimeoutUtil.isTimeout(ex)) {
                throw new RuntimeException(TimeoutUtil.GENERIC_TIMEOUT_MESSAGE, ex);
            }
            throw ex;
        }

        emitPlanUpdate();
        return response.toString();
    }

    /** 获取当前旅游计划对象（含子任务列表与状态） */
    public Plan getCurrentPlan() {
        return planNotebook.getCurrentPlan();
    }

    /**
     * 是否应自动推进：计划存在且状态为 IN_PROGRESS（仍有待执行子任务）。
     * Web 端在 done 事件中据此决定是否链式发起 auto 请求。
     */
    public boolean shouldAutoContinue() {
        Plan plan = getCurrentPlan();
        return plan != null && plan.getState() == PlanState.IN_PROGRESS;
    }

    /** 计划是否已结束（完成、放弃或不存在） */
    public boolean isFinished() {
        Plan plan = getCurrentPlan();
        return plan == null
                || plan.getState() == PlanState.DONE
                || plan.getState() == PlanState.ABANDONED;
    }

    /** 控制台模式下的单轮流式输出，直接打印到 stdout */
    private void streamTurnToConsole(Msg userMsg) {
        agent.stream(userMsg)
                .doOnNext(event -> System.out.println(safeText(event)))
                .blockLast();
    }

    /** 从流式事件中安全提取文本内容，空事件返回空字符串 */
    private static String safeText(Event event) {
        if (event == null || event.getMessage() == null) {
            return "";
        }
        String text = event.getMessage().getTextContent();
        return text == null ? "" : text;
    }

    /** 将当前计划序列化为 JSON 并通过 eventSink 推送 plan 事件 */
    private void emitPlanUpdate() {
        emit(ChatEvent.plan(PlanMapper.toJson(getCurrentPlan())));
    }

    /** 向 Web 事件回调发送事件；控制台模式（eventSink 为 null）时忽略 */
    private void emit(ChatEvent event) {
        if (eventSink != null) {
            eventSink.accept(event);
        }
    }

    /** 将纯文本包装为 AgentScope 用户消息 */
    private static Msg toUserMessage(String text) {
        return Msg.builder()
                .role(MsgRole.USER)
                .content(List.of(TextBlock.builder().text(text).build()))
                .build();
    }
}
