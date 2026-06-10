package managerAgent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条对话历史记录 DTO。
 * <p>
 * 存储在 {@link managerAgent.model.ChatSession} 内存中，
 * 通过 {@code GET /api/session/{sessionId}/history} 返回给前端，
 * 用于页面刷新后恢复聊天记录与计划面板。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRecord {

    /**
     * 消息归属角色，用于前端区分展示样式。
     * 取值：{@code user}、{@code agent}、{@code system}、{@code plan}
     */
    private String role;

    /**
     * 消息细分类别，与 SSE 事件名或 {@link ChatEventType} 对应。
     * 如：{@code user}、{@code message}、{@code thinking}、{@code tool}、{@code plan}、{@code error}
     */
    private String type;

    /** 消息正文；plan 类型时为 JSON 字符串 */
    private String content;

    /** 消息产生时间的 Unix 毫秒时间戳 */
    private long timestamp;

    /**
     * 将流式 {@link ChatEvent} 转为可持久化的历史记录。
     * <p>
     * 由 {@link managerAgent.model.ChatSession} 在接收 Hook 事件时调用，
     * 将 {@link ChatEventType} 映射为前端可识别的 role/type 字符串。
     *
     * @param event Agent 推理过程中产生的事件
     * @return 可存入历史列表的记录对象
     */
    public static ChatMessageRecord from(ChatEvent event) {
        String role = switch (event.getType()) {
            case USER -> "user";
            case THINKING, TOOL, AUTO -> "system";
            case MESSAGE -> "agent";
            case PLAN -> "plan";
            case DONE -> "system";
        };
        return new ChatMessageRecord(
                role,
                event.getType().name().toLowerCase(),
                event.getContent(),
                event.getTimestamp());
    }
}
