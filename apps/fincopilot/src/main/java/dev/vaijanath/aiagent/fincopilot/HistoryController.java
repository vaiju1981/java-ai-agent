package dev.vaijanath.aiagent.fincopilot;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import dev.vaijanath.aiagent.memory.ConversationHistory;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.springboot.web.AgentTurns;
import java.util.List;
import java.util.Locale;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lets a signed-in user browse their own past conversations: list their sessions and replay one. History
 * is read through the platform's {@link ConversationHistory} seam, scoped by tenant — which FinCopilot
 * sets to the user id (see {@link ChatController}) — so a user only ever sees their own chats. Replayed
 * messages are filtered to the user/assistant turns with visible text; system prompts and tool plumbing
 * are omitted. Guarded by {@link SessionAuthenticationFilter} (it covers {@code /api/chat/*}).
 */
@RestController
@RequestMapping("/api/chat/sessions")
class HistoryController {

    private final ConversationHistory history;

    HistoryController(ConversationHistory history) {
        this.history = history;
    }

    @GetMapping
    List<SessionView> sessions(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return history.listSessions(principal).stream()
                .map(s -> new SessionView(s.sessionId(), s.messageCount(), s.lastActivity().toEpochMilli()))
                .toList();
    }

    @GetMapping("/{sessionId}")
    List<MessageView> messages(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal,
            @PathVariable String sessionId) {
        AgentTurns.requireIdentifier(sessionId, "sessionId");
        return history.messages(principal, sessionId).stream()
                .filter(m -> m.role() == Role.USER || m.role() == Role.ASSISTANT)
                .filter(m -> !m.content().isBlank())
                .map(HistoryController::toView)
                .toList();
    }

    @DeleteMapping("/{sessionId}")
    ResponseEntity<Void> delete(
            @RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal,
            @PathVariable String sessionId) {
        AgentTurns.requireIdentifier(sessionId, "sessionId");
        history.delete(principal, sessionId);
        return ResponseEntity.noContent().build();
    }

    private static MessageView toView(Message message) {
        return new MessageView(message.role().name().toLowerCase(Locale.ROOT), message.content());
    }

    record SessionView(String sessionId, long messageCount, long lastActivityMillis) {}

    record MessageView(String role, String content) {}
}
