package dev.vaijanath.aiagent.fincopilot.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import dev.vaijanath.aiagent.store.jdbc.ConnectionSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Validation-path tests for the ledger endpoints that reject before touching the database. */
class LedgerControllerTest {

    private static final String PRINCIPAL = SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // These requests must be rejected before any store call; a connection attempt is a test failure.
        ConnectionSource noDatabase = () -> {
            throw new AssertionError("validation must reject before touching the database");
        };
        mvc = standaloneSetup(new LedgerController(new AccountStore(noDatabase), new TransactionStore(noDatabase)))
                .build();
    }

    @Test
    void accountWithoutNameIsBadRequest() throws Exception {
        mvc.perform(post("/api/accounts")
                        .requestAttr(PRINCIPAL, "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transactionMissingFieldsIsBadRequest() throws Exception {
        mvc.perform(post("/api/transactions")
                        .requestAttr(PRINCIPAL, "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void importMissingFieldsIsBadRequest() throws Exception {
        mvc.perform(post("/api/transactions/import")
                        .requestAttr(PRINCIPAL, "user-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
