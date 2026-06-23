package dev.vaijanath.aiagent.fincopilot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import dev.vaijanath.aiagent.memory.ConversationHistory;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ToolCall;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

/** Verifies the history HTTP contract against a stubbed ConversationHistory (no database). */
class HistoryControllerTest {

    private static final String PRINCIPAL = SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE;

    /** A configurable in-test ConversationHistory. */
    private static final class FakeHistory implements ConversationHistory {
        List<SessionSummary> sessions = List.of();
        List<Message> history = List.of();
        String deleted;

        @Override
        public List<SessionSummary> listSessions(String tenant) {
            return sessions;
        }

        @Override
        public List<Message> messages(String tenant, String sessionId) {
            return history;
        }

        @Override
        public void delete(String tenant, String sessionId) {
            deleted = tenant + "/" + sessionId;
        }
    }

    private MockMvc mvc(ConversationHistory history) {
        return standaloneSetup(new HistoryController(history)).build();
    }

    @Test
    void listsTheUsersSessions() throws Exception {
        FakeHistory history = new FakeHistory();
        history.sessions = List.of(new ConversationHistory.SessionSummary("s-1", 4, Instant.ofEpochMilli(1000)));

        mvc(history)
                .perform(get("/api/chat/sessions").requestAttr(PRINCIPAL, "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("s-1"))
                .andExpect(jsonPath("$[0].messageCount").value(4))
                .andExpect(jsonPath("$[0].lastActivityMillis").value(1000));
    }

    @Test
    void replaysOnlyUserAndAssistantTextHidingSystemAndToolPlumbing() throws Exception {
        FakeHistory history = new FakeHistory();
        history.history = List.of(
                Message.system("you are FinCopilot"),
                Message.user("hi"),
                Message.assistant("", List.of(new ToolCall("id", "finance_summary", "{}"))),
                Message.toolResult("id", "finance_summary", "42"),
                Message.assistant("here is your summary"));

        mvc(history)
                .perform(get("/api/chat/sessions/s-1").requestAttr(PRINCIPAL, "u1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("hi"))
                .andExpect(jsonPath("$[1].role").value("assistant"))
                .andExpect(jsonPath("$[1].content").value("here is your summary"));
    }

    @Test
    void rejectsAMalformedSessionId() throws Exception {
        mvc(new FakeHistory())
                .perform(get("/api/chat/sessions/bad@id").requestAttr(PRINCIPAL, "u1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesTheSessionScopedToTheUser() throws Exception {
        FakeHistory history = new FakeHistory();

        mvc(history)
                .perform(delete("/api/chat/sessions/s-1").requestAttr(PRINCIPAL, "u1"))
                .andExpect(status().isNoContent());

        assertEquals("u1/s-1", history.deleted, "delete is scoped to the authenticated user and session");
    }
}
