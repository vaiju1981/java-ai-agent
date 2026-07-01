package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.agent.Trust;
import dev.vaijanath.aiagent.audit.FileAuditSink;
import dev.vaijanath.aiagent.deep.DeepAgent;
import dev.vaijanath.aiagent.deep.InMemoryWorkspace;
import dev.vaijanath.aiagent.deep.Plan;
import dev.vaijanath.aiagent.deep.PlanStep;
import dev.vaijanath.aiagent.deep.Planner;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.rag.InMemoryVectorStore;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A production-shaped incident demo: DAG planning, parallel specialist workers, RAG-backed runbooks,
 * governed execution, durable audit, and workspace artifacts.
 */
public final class IncidentResponseWarRoom {

    private IncidentResponseWarRoom() {}

    public record DemoRun(String output, Map<String, String> workspace, Path auditLog) {}

    public static void main(String[] args) throws IOException {
        Path audit = Files.createTempFile("java-ai-agent-incident-", ".audit");
        DemoRun run = runDemo(audit);

        System.out.println("== IncidentResponseWarRoom ==\n");
        System.out.println(run.output());
        System.out.println("\nWorkspace artifacts:");
        run.workspace().keySet().stream().sorted().forEach(path -> System.out.println("  " + path));
        System.out.println("\nAudit log: " + run.auditLog());
    }

    public static DemoRun runDemo(Path auditLog) throws IOException {
        InMemoryVectorStore runbooks = runbookStore();
        InMemoryWorkspace workspace = new InMemoryWorkspace();

        DeepAgent deepAgent = DeepAgent.builder()
                .planner(new IncidentPlanner())
                .worker(() -> new IncidentWorker(runbooks))
                .synthesizer(new IncidentSynthesizer())
                .workspace(workspace)
                .parallel(true)
                .stepTimeout(Duration.ofSeconds(10))
                .build();

        try (FileAuditSink audit = new FileAuditSink(auditLog)) {
            Agent governed = Trust.govern(deepAgent, audit, List.of(new CardDataGuardrail()));
            AgentResponse response = governed.run(new AgentRequest(incidentBrief(), incidentContext()));
            return new DemoRun(response.output(), workspace.files(), auditLog);
        }
    }

    private static RequestContext incidentContext() {
        return new RequestContext(
                "checkout-incident-2026-06-30",
                "sre.alex",
                "merchant-platform",
                "trace-checkout-incident-001",
                Instant.now().plusSeconds(30),
                Map.of("service", "checkout", "env", "prod"));
    }

    private static String incidentBrief() {
        return "SEV-1 checkout incident: card-tokenization errors are spiking in us-west after the "
                + "payments-api deploy. A customer report pasted test card 4111-1111-1111-1111; "
                + "do not echo raw card data. Prepare an executive-ready mitigation plan.";
    }

    private static InMemoryVectorStore runbookStore() {
        InMemoryVectorStore store = new InMemoryVectorStore(DemoEmbeddings::keywordHash);
        store.add("merchant-platform", "runbook-payment-tokenization",
                "Payment-tokenization incident runbook: disable tokenization-provider-v2 before "
                        + "rolling back binaries; verify gateway auth rate, checkout p95, and queue depth.");
        store.add("merchant-platform", "comms-sev1",
                "SEV-1 communications policy: publish first customer update within 15 minutes, "
                        + "avoid unverified root cause, state mitigation underway, and share next update time.");
        store.add("merchant-platform", "post-incident",
                "Post-incident policy: preserve deploy evidence, open a blameless review within 48 hours, "
                        + "and attach metrics, timeline, customer impact, and prevention actions.");
        return store;
    }

    private static final class IncidentPlanner implements Planner {
        @Override
        public Plan plan(String task) {
            return new Plan(List.of(
                    new PlanStep(1, "Inspect checkout and payment telemetry for blast radius"),
                    new PlanStep(2, "Review recent deploy changes and feature flags"),
                    new PlanStep(3, "Retrieve runbook and customer communications obligations"),
                    new PlanStep(4, "Combine evidence into mitigation, owner, and communications plan",
                            List.of(1, 2, 3))));
        }
    }

    private static final class IncidentWorker implements Agent {
        private final InMemoryVectorStore runbooks;

        private IncidentWorker(InMemoryVectorStore runbooks) {
            this.runbooks = runbooks;
        }

        @Override
        public AgentResponse run(AgentRequest request) {
            String input = request.input().toLowerCase();
            if (input.contains("telemetry")) {
                return AgentResponse.completed(
                        "Telemetry: checkout error rate rose from 0.2% to 12.4% at 09:42 PT; "
                                + "p95 latency is 3.8s; failures concentrate on card tokenization in us-west; "
                                + "18,420 payment attempts failed; no evidence of stored-data loss.");
            }
            if (input.contains("deploy")) {
                return AgentResponse.completed(
                        "Deploy review: payments-api 2026.06.30.2 enabled feature flag "
                                + "tokenization-provider-v2 at 25% in us-west. The database migration is read-only. "
                                + "Safest mitigation is disabling the flag before binary rollback.");
            }
            if (input.contains("runbook") || input.contains("communications")) {
                List<RetrievedChunk> hits = runbooks.retrieve(
                        request.context().tenant(), "payment tokenization sev1 customer communications", 3);
                String evidence = hits.stream()
                        .map(hit -> hit.id() + ": " + hit.text())
                        .collect(Collectors.joining("\n"));
                return AgentResponse.completed("Retrieved guidance:\n" + evidence);
            }
            return AgentResponse.completed(
                    "Mitigation plan: disable tokenization-provider-v2 in us-west, verify checkout "
                            + "error rate below 1%, keep payments-api rollback warm, assign SRE lead to "
                            + "metrics, payments lead to flag rollback, support lead to customer updates.");
        }
    }

    private static final class IncidentSynthesizer implements ModelPort {
        @Override
        public ModelResponse chat(ModelRequest request) {
            String prompt = request.messages().stream()
                    .filter(message -> message.role() == dev.vaijanath.aiagent.model.Role.USER)
                    .reduce((a, b) -> b)
                    .map(Message::content)
                    .orElse("");
            String confidence = prompt.contains("Retrieved guidance") ? "high" : "medium";
            return ModelResponse.text(
                    """
                    Executive incident plan
                    Severity: SEV-1 checkout/payment degradation
                    Confidence: %s

                    Current read: checkout failures are tied to card tokenization in us-west after the
                    payments-api 2026.06.30.2 rollout. Impact is active but bounded to checkout attempts;
                    there is no evidence of stored-data loss.

                    Immediate mitigation:
                    1. Disable tokenization-provider-v2 in us-west.
                    2. Watch checkout error rate, payment auth rate, p95 latency, and queue depth for 10 minutes.
                    3. Keep binary rollback ready, but use it only if the flag rollback does not recover traffic.

                    Customer communications:
                    Publish a first SEV-1 update within 15 minutes, avoid unverified root cause language,
                    say mitigation is underway, and name the next update time.

                    Follow-up:
                    Preserve deploy evidence and open a blameless review within 48 hours with timeline,
                    impact, root cause, and prevention actions.
                    """.formatted(confidence).strip(),
                    new Usage(1_250, 620));
        }

        @Override
        public String name() {
            return "demo-incident-synthesizer";
        }
    }

    private static final class CardDataGuardrail implements Guardrail {
        private static final Pattern CARD = Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b");

        @Override
        public GuardrailDecision check(GuardrailStage stage, String content) {
            String scrubbed = CARD.matcher(content).replaceAll("[redacted-card]");
            return GuardrailDecision.allow(scrubbed);
        }

        @Override
        public String name() {
            return "card-data-scrubber";
        }
    }
}
