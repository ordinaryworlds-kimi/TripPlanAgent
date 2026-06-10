package managerAgent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import managerAgent.agents.ManagerAgent;
import managerAgent.dto.ChatEvent;
import managerAgent.dto.ChatEventType;
import managerAgent.dto.ChatMessageRecord;
import managerAgent.model.ChatSession;
import managerAgent.util.PlanMapper;
import managerAgent.util.TimeoutUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Web 聊天会话服务：管理多会话状态、SSE 流式推送与计划自动推进。
 * <p>
 * 每个 sessionId 对应独立的 {@link ManagerAgent} 实例与对话历史，
 * 支持页面刷新后通过 history 接口恢复记录。
 */
@Slf4j
@Service
public class ChatSessionService {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 15;

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ScheduledExecutorService heartbeatScheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "sse-heartbeat");
                t.setDaemon(true);
                return t;
            });

    /**
     * 创建新会话并返回唯一 sessionId。
     * 前端通常将 sessionId 存入 localStorage 以支持刷新恢复。
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new ChatSession(sessionId));
        return sessionId;
    }

    /**
     * 按 sessionId 获取会话，不存在则懒创建。
     *
     * @param sessionId 会话标识
     * @return 对应的 ChatSession（含 ManagerAgent 与历史记录）
     */
    public ChatSession getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, ChatSession::new);
    }

    /**
     * 获取会话的对话历史，并更新最后访问时间。
     */
    public List<ChatMessageRecord> getHistory(String sessionId) {
        ChatSession session = getOrCreate(sessionId);
        session.touch();
        return session.getHistory();
    }

    /**
     * SSE 流式聊天入口，由 {@code ChatController} 调用。
     * <p>
     * 流程：执行用户轮次或自动推进 → 发送 done 事件（含 autoContinue 标志）→ 关闭连接。
     *
     * @param sessionId 会话 ID
     * @param prompt    用户输入；autoOnly 为 true 时可忽略
     * @param autoOnly  true 时仅自动推进 IN_PROGRESS 子任务，不处理新用户输入
     * @param emitter   Spring SSE 发射器
     */
    public void streamChat(String sessionId, String prompt, boolean autoOnly, SseEmitter emitter) {
        ChatSession session = getOrCreate(sessionId);
        boolean autoContinue = false;
        try {
            if (autoOnly) {
                runAutoProgress(session, emitter);
            } else {
                runUserTurn(session, prompt, emitter);
            }
            autoContinue = session.getManagerAgent().shouldAutoContinue();
        } catch (Exception ex) {
            handleError(session, emitter, ex);
        } finally {
            try {
                emit(emitter, "done", Map.of("autoContinue", autoContinue));
                emitter.complete();
            } catch (IOException | IllegalStateException ignored) {
            }
        }
    }

    /**
     * 处理用户主动发起的一轮对话：推送 user 事件、写入历史、执行 Agent 轮次。
     */
    private void runUserTurn(ChatSession session, String prompt, SseEmitter emitter) throws IOException {
        session.touch();
        emit(emitter, "user", Map.of("content", prompt));
        session.addRecord(new ChatMessageRecord("user", "user", prompt, System.currentTimeMillis()));
        executeTurn(session, prompt, emitter);
    }

    /**
     * 自动推进模式：当计划状态为 IN_PROGRESS 时，循环发送 AUTO_CONTINUE_PROMPT
     * 直到所有子任务执行完毕或计划状态改变。
     */
    private void runAutoProgress(ChatSession session, SseEmitter emitter) throws IOException {
        ManagerAgent agent = session.getManagerAgent();
        while (agent.shouldAutoContinue()) {
            emit(emitter, "auto", Map.of("content", ManagerAgent.AUTO_CONTINUE_PROMPT));
            session.addRecord(new ChatMessageRecord(
                    "system", "auto", ManagerAgent.AUTO_CONTINUE_PROMPT, System.currentTimeMillis()));
            executeTurn(session, ManagerAgent.AUTO_CONTINUE_PROMPT, emitter);
        }
    }

    /**
     * 执行单轮 Agent 推理：绑定事件转发、启动心跳、调用 streamTurn、推送计划快照。
     * <p>
     * 心跳（ping 事件）每 15 秒发送一次，防止长时间工具调用导致 SSE 连接超时断开。
     */
    private void executeTurn(ChatSession session, String prompt, SseEmitter emitter) throws IOException {
        session.setActiveSink(event -> forwardEvent(emitter, event));
        ScheduledFuture<?> heartbeat = startHeartbeat(emitter);
        try {
            String response = session.getManagerAgent().streamTurn(prompt);
            if (!response.isBlank()) {
                session.addRecord(new ChatMessageRecord(
                        "agent", "message", response, System.currentTimeMillis()));
            }
            var planDto = PlanMapper.toDto(session.getManagerAgent().getCurrentPlan());
            emit(emitter, "plan", planDto);
            if (planDto != null) {
                session.addRecord(new ChatMessageRecord(
                        "plan",
                        "plan",
                        objectMapper.writeValueAsString(planDto),
                        System.currentTimeMillis()));
            }
        } finally {
            heartbeat.cancel(true);
            session.clearActiveSink();
        }
    }

    /**
     * 统一异常处理：解析用户可读消息、写入历史、推送 error 事件。
     */
    private void handleError(ChatSession session, SseEmitter emitter, Exception ex) {
        String message = TimeoutUtil.resolveMessage(ex);
        logError(ex, message);
        session.addRecord(new ChatMessageRecord(
                "system", "error", message, System.currentTimeMillis()));
        try {
            emit(emitter, "error", Map.of("message", message));
        } catch (IOException ignored) {
        }
    }

    /** 超时记 warn 日志，其他异常记 error 并附带堆栈 */
    private void logError(Exception ex, String message) {
        if (TimeoutUtil.isTimeout(ex)) {
            log.warn(message);
        } else {
            log.error(message, ex);
        }
    }

    /**
     * 启动 SSE 心跳定时任务，在长耗时推理期间保持连接活跃。
     *
     * @return 可被取消的 ScheduledFuture
     */
    private ScheduledFuture<?> startHeartbeat(SseEmitter emitter) {
        return heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emit(emitter, "ping", Map.of("content", "processing"));
            } catch (IOException ignored) {
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * 将 ManagerAgent 内部 ChatEvent 转为 SSE 事件名并推送。
     * PLAN / DONE 类型由 executeTurn 单独处理，此处跳过避免重复。
     */
    private void forwardEvent(SseEmitter emitter, ChatEvent event) {
        if (event.getType() == ChatEventType.PLAN || event.getType() == ChatEventType.DONE) {
            return;
        }
        String eventName = switch (event.getType()) {
            case THINKING -> "thinking";
            case TOOL -> "tool";
            case MESSAGE -> "message";
            case AUTO -> "auto";
            case USER -> "user";
            default -> "info";
        };
        try {
            emit(emitter, eventName, Map.of("content", event.getContent()));
        } catch (IOException ignored) {
        }
    }

    /**
     * 向 SSE 连接发送具名事件，payload 序列化为 JSON 字符串。
     *
     * @param emitter   SSE 发射器
     * @param eventName 事件名（thinking / tool / message / plan / done / error / ping 等）
     * @param payload   任意可序列化对象
     */
    private void emit(SseEmitter emitter, String eventName, Object payload) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(objectMapper.writeValueAsString(payload)));
    }
}
