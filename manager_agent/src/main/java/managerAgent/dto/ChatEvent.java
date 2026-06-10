package managerAgent.dto;

import lombok.Getter;

import java.time.Instant;

/**
 * Agent 推理过程中的流式事件对象。
 * <p>
 * 数据流向：{@link managerAgent.hook.planHook} 产生事件
 * → {@link managerAgent.agents.ManagerAgent} 的 eventSink 接收
 * → {@link managerAgent.service.ChatSessionService} 转为 SSE 推送给前端。
 * <p>
 * 本类为不可变对象；{@code timestamp} 在构造时自动写入，保证时序准确。
 * 推荐使用静态工厂方法（{@link #thinking}、{@link #tool} 等）创建实例。
 */
@Getter
public class ChatEvent {

    /** 事件类型，决定 SSE 事件名与前端渲染方式 */
    private final ChatEventType type;

    /** 事件内容：思考文本、工具描述、回复片段或计划 JSON */
    private final String content;

    /** 事件产生时间的 Unix 毫秒时间戳 */
    private final long timestamp;

    /**
     * 构造事件，自动记录当前时间戳。
     *
     * @param type    事件类型
     * @param content 事件正文
     */
    public ChatEvent(ChatEventType type, String content) {
        this.type = type;
        this.content = content;
        this.timestamp = Instant.now().toEpochMilli();
    }

    /** 创建用户输入事件 */
    public static ChatEvent user(String content) {
        return new ChatEvent(ChatEventType.USER, content);
    }

    /** 创建 Agent 思考过程事件（ReAct 推理中的 reasoning） */
    public static ChatEvent thinking(String content) {
        return new ChatEvent(ChatEventType.THINKING, content);
    }

    /** 创建工具调用事件（开始或完成远程 Agent 调用等） */
    public static ChatEvent tool(String content) {
        return new ChatEvent(ChatEventType.TOOL, content);
    }

    /** 创建 Agent 回复片段事件，流式累积为完整回复 */
    public static ChatEvent message(String content) {
        return new ChatEvent(ChatEventType.MESSAGE, content);
    }

    /** 创建自动推进子任务事件（服务端发送 AUTO_CONTINUE_PROMPT 时） */
    public static ChatEvent auto(String content) {
        return new ChatEvent(ChatEventType.AUTO, content);
    }

    /** 创建计划更新事件，content 为计划 JSON 字符串 */
    public static ChatEvent plan(String content) {
        return new ChatEvent(ChatEventType.PLAN, content);
    }

    /** 创建轮次结束标记事件（当前实现中主要由 SSE done 事件承担此职责） */
    public static ChatEvent done() {
        return new ChatEvent(ChatEventType.DONE, "");
    }
}
