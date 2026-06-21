package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import java.util.Objects;

/** Classifies a {@link Ticket} into a structured triage decision using {@link StructuredOutput}. */
final class TicketTriager {

    /** The structured triage decision the model fills in (bound from JSON, no parsing). */
    public record TriageResult(String priority, String category, String team) {
    }

    private final StructuredOutput structured;

    TicketTriager(StructuredOutput structured) {
        this.structured = Objects.requireNonNull(structured, "structured");
    }

    TriageResult triage(Ticket ticket) {
        String prompt = "Triage this support ticket.\n"
                + "priority: one of low, medium, high, urgent.\n"
                + "category: one of Billing, Bug, HowTo, Account, FeatureRequest, Other.\n"
                + "team: one of Payments, Engineering, Support, Accounts, Product.\n\n"
                + "Subject: " + ticket.subject() + "\nBody: " + ticket.body();
        return structured.generate(ModelRequest.of(List.of(Message.user(prompt))), TriageResult.class);
    }
}
