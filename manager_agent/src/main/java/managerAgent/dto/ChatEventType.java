package managerAgent.dto;

/**
 * 聊天流式事件类型枚举。
 * <p>
 * 每种类型对应 SSE 中的一种事件名（由 {@link managerAgent.service.ChatSessionService} 映射），
 * 前端据此决定渲染为普通消息、思考气泡、工具调用提示或计划面板更新。
 */
public enum ChatEventType {

    /** 用户发送的输入 */
    USER,

    /** Agent ReAct 推理中的思考/推理文本 */
    THINKING,

    /** 工具调用相关提示（如正在调用路线制定 Agent） */
    TOOL,

    /** Agent 回复的正文片段（流式拼接） */
    MESSAGE,

    /** 服务端自动推进 IN_PROGRESS 子任务时的系统提示 */
    AUTO,

    /** 旅游计划状态变更（对应 plan 面板刷新） */
    PLAN,

    /** 单轮对话结束标记 */
    DONE
}
