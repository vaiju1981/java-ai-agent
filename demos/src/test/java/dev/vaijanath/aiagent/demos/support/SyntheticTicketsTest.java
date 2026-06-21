package dev.vaijanath.aiagent.demos.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class SyntheticTicketsTest {

    @Test
    void generatesTheRequestedCount() {
        assertEquals(20, SyntheticTickets.generate(20).size());
    }

    @Test
    void ticketsHaveContent() {
        Ticket t = SyntheticTickets.generate(1).get(0);
        assertFalse(t.subject().isBlank());
        assertFalse(t.body().isBlank());
    }
}
