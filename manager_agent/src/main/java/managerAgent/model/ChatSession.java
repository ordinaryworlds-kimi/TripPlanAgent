package managerAgent.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import managerAgent.agents.ManagerAgent;
import managerAgent.dto.ChatEvent;
import managerAgent.dto.ChatEventType;
import managerAgent.dto.ChatMessageRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Web 聊天会话模型：持有独立的 {@link ManagerAgent}、对话历史与 SSE 事件转发回调。
 */
@Getter
public class ChatSession {

    private final String sessionId;
    private final ManagerAgent managerAgent;

    /** 历史列表不通过 Lombok 暴露 getter，由 {@link #getHistory()} 返回不可变副本 */
    @Getter(AccessLevel.NONE)
    private final List<ChatMessageRecord> history = new ArrayList<>();

    private final Object lock = new Object();

    /** 当前 SSE 连接的事件转发回调，轮次结束后由 {@link #clearActiveSink()} 置空 */
    @Setter
    private Consumer<ChatEvent> activeSink;

    private long lastAccessAt;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.managerAgent = new ManagerAgent(this::dispatchEvent);
        this.lastAccessAt = Instant.now().toEpochMilli();
    }

    public void clearActiveSink() {
        this.activeSink = null;
    }

    public void touch() {
        lastAccessAt = Instant.now().toEpochMilli();
    }

    /**
     * 返回历史记录的不可变副本，避免外部直接修改内部列表。
     */
    public List<ChatMessageRecord> getHistory() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<>(history));
        }
    }

    public void addRecord(ChatMessageRecord record) {
        synchronized (lock) {
            history.add(record);
            lastAccessAt = Instant.now().toEpochMilli();
        }
    }

    /**
     * ManagerAgent Hook 回调：持久化非 MESSAGE/DONE 事件，并转发给当前 SSE 连接。
     */
    private void dispatchEvent(ChatEvent event) {
        if (event.getType() != ChatEventType.DONE && event.getType() != ChatEventType.MESSAGE) {
            addRecord(ChatMessageRecord.from(event));
        }
        if (activeSink != null) {
            activeSink.accept(event);
        }
    }
}
