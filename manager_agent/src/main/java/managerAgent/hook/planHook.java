package managerAgent.hook;

import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostActingEvent;
import io.agentscope.core.hook.PostReasoningEvent;
import io.agentscope.core.hook.PreActingEvent;
import io.agentscope.core.hook.PreReasoningEvent;
import io.agentscope.core.plan.PlanNotebook;
import lombok.extern.slf4j.Slf4j;
import managerAgent.dto.ChatEvent;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@Slf4j
public class planHook implements Hook {

    private final Consumer<ChatEvent> eventSink;

    public planHook(PlanNotebook planNotebook) {
        this(planNotebook, null);
    }

    public planHook(PlanNotebook planNotebook, Consumer<ChatEvent> eventSink) {
        this.eventSink = eventSink;
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        switch (event) {
            case PreReasoningEvent e -> {
                String prompt = e.getInputMessages().get(0).getTextContent();
                log.info("#### 用户的Prompt：#######");
                log.info(prompt);
            }
            case PostReasoningEvent e -> {
                String reason = e.getReasoningMessage().getTextContent();
                log.info("#### 思考过程：#######");
                log.info(reason);
                emit(ChatEvent.thinking(reason));
            }
            case PreActingEvent e -> {
                String toolName = e.getToolUse().getName();
                log.info("##### 准备调用工具：" + toolName);
                emit(ChatEvent.tool("正在调用工具：" + describeTool(toolName)));
            }
            case PostActingEvent e -> {
                String toolName = e.getToolUse().getName();
                log.info("##### 调用工具完成：" + toolName);
                emit(ChatEvent.tool("工具调用完成：" + describeTool(toolName)));
            }
            default -> {
            }
        }
        return Mono.just(event);
    }

    private void emit(ChatEvent chatEvent) {
        if (eventSink != null) {
            eventSink.accept(chatEvent);
        }
    }

    private static String describeTool(String toolName) {
        return switch (toolName) {
            case "callRouteMakingAgent" -> "路线制定智能体（RouteMakingAgent）";
            case "callTripPlannerAgent" -> "行程规划智能体（TripPlannerAgent）";
            default -> toolName;
        };
    }
}
