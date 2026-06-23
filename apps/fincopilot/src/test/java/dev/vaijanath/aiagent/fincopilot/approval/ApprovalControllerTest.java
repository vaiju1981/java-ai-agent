package dev.vaijanath.aiagent.fincopilot.approval;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class ApprovalControllerTest {

    private static final String PRINCIPAL = SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE;

    @Test
    void approveResolvesThePendingFutureForTheOwner() throws Exception {
        PendingApprovals pending = new PendingApprovals();
        CompletableFuture<Boolean> future = pending.register("ap-1", "u1");
        MockMvc mvc = standaloneSetup(new ApprovalController(pending)).build();

        mvc.perform(post("/api/chat/approve")
                        .requestAttr(PRINCIPAL, "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalId\":\"ap-1\",\"approved\":true}"))
                .andExpect(status().isNoContent());

        assertTrue(future.get());
    }

    @Test
    void approveUnknownApprovalReturns404() throws Exception {
        MockMvc mvc = standaloneSetup(new ApprovalController(new PendingApprovals())).build();

        mvc.perform(post("/api/chat/approve")
                        .requestAttr(PRINCIPAL, "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvalId\":\"nope\",\"approved\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void approveWithoutAnIdReturns400() throws Exception {
        MockMvc mvc = standaloneSetup(new ApprovalController(new PendingApprovals())).build();

        mvc.perform(post("/api/chat/approve")
                        .requestAttr(PRINCIPAL, "u1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
