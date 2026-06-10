package managerAgent.controller;

import managerAgent.dto.ChatMessageRecord;
import managerAgent.dto.InfrastructureStatusDto;
import managerAgent.service.ChatSessionService;
import managerAgent.service.InfrastructureStatusService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ChatSessionService chatSessionService;
    private final InfrastructureStatusService infrastructureStatusService;

    public ChatController(
            ChatSessionService chatSessionService,
            InfrastructureStatusService infrastructureStatusService) {
        this.chatSessionService = chatSessionService;
        this.infrastructureStatusService = infrastructureStatusService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/status")
    public InfrastructureStatusDto status() {
        return infrastructureStatusService.check();
    }

    @PostMapping("/session")
    public Map<String, String> createSession() {
        return Map.of("sessionId", chatSessionService.createSession());
    }

    @GetMapping("/session/{sessionId}/history")
    public List<ChatMessageRecord> history(@PathVariable("sessionId") String sessionId) {
        return chatSessionService.getHistory(sessionId);
    }

    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(
            @RequestParam("sessionId") String sessionId,
            @RequestParam(value = "prompt", defaultValue = "") String prompt,
            @RequestParam(value = "auto", defaultValue = "false") boolean autoOnly) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Schedulers.boundedElastic()
                .schedule(() -> chatSessionService.streamChat(sessionId, prompt, autoOnly, emitter));
        return emitter;
    }
}
