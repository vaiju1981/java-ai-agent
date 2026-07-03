package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.eval.EvalCase;
import dev.vaijanath.aiagent.eval.EvalReport;
import dev.vaijanath.aiagent.eval.Evaluator;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ReplayModelPort;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.observe.RecordingObserver;
import dev.vaijanath.aiagent.observe.TokenAccountingObserver;
import dev.vaijanath.aiagent.tool.ReplayToolExecutor;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolApprovers;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * A controlled operations copilot demo backed by a real SQLite database: multi-tool orchestration,
 * transactional writes, effectful-tool denial, idempotency, recording/replay, token accounting, and evals.
 */
public final class ControlledOperationsCopilot {

    private ControlledOperationsCopilot() {}

    public record DemoRun(
            Path database,
            String decision,
            String refundAttempt,
            String approvedRefund,
            String replayedDecision,
            int readOnlyDbToolInvocations,
            int effectfulDbToolInvocations,
            int persistedRefunds,
            long modelCalls,
            long totalTokens,
            EvalReport evalReport) {}

    public static void main(String[] args) {
        DemoRun run = runDemo();

        System.out.println("== ControlledOperationsCopilot ==\n");
        System.out.println("Decision:");
        System.out.println(run.decision());
        System.out.println("\nEffectful operation attempt:");
        System.out.println(run.refundAttempt());
        System.out.println("\nAuthorized operation:");
        System.out.println(run.approvedRefund());
        System.out.println("\nReplay:");
        System.out.println(run.replayedDecision());
        System.out.printf(Locale.ROOT,
                "%nDatabase=%s%nRead-only DB tool invocations=%d, effectful DB tool invocations=%d, "
                        + "persisted refunds=%d, model calls=%d, tokens=%d%n",
                run.database(),
                run.readOnlyDbToolInvocations(),
                run.effectfulDbToolInvocations(),
                run.persistedRefunds(),
                run.modelCalls(),
                run.totalTokens());
        System.out.println("\nEval:");
        System.out.print(run.evalReport().render());
    }

    public static DemoRun runDemo() {
        return runDemo(tempDatabase("java-ai-agent-ops-"));
    }

    public static DemoRun runDemo(Path database) {
        try (OperationsDatabase db = OperationsDatabase.open(database)) {
            return runDemo(database, db);
        }
    }

    private static DemoRun runDemo(Path database, OperationsDatabase db) {
        OperationsTools tools = new OperationsTools(db);
        RecordingObserver recording = new RecordingObserver();
        TokenAccountingObserver tokens = new TokenAccountingObserver();

        Agent agent = buildAgent(new ScriptedOperationsModel(), tools, recording, tokens, false);
        AgentResponse decision = agent.run(new AgentRequest(
                "Customer Acme Outdoor Supply asks whether invoice INV-771 qualifies for a courtesy credit."));

        AgentResponse refundAttempt = buildAgent(new ScriptedOperationsModel(), tools, null, null, false)
                .run(new AgentRequest("Issue the $35 courtesy refund for Acme Outdoor Supply now."));

        AgentResponse approvedRefund = buildAgent(new ScriptedOperationsModel(), tools, null, null, true)
                .run(new AgentRequest("Supervisor sup-91 approved this action: issue the $35 courtesy refund now."));
        buildAgent(new ScriptedOperationsModel(), tools, null, null, true)
                .run(new AgentRequest("Supervisor sup-91 approved this action: issue the $35 courtesy refund now."));

        AgentResponse replayed = DefaultAgent.builder()
                .model(new ReplayModelPort(recording.modelResponses()))
                .tool(tools.accountLookup())
                .tool(tools.invoiceLookup())
                .tool(tools.policyLookup())
                .tool(tools.issueRefund())
                .toolExecutor(new ReplayToolExecutor(recording.toolResults()))
                .maxSteps(6)
                .build()
                .run(new AgentRequest(
                        "Customer Acme Outdoor Supply asks whether invoice INV-771 qualifies for a courtesy credit."));

        EvalReport evalReport = evalReport();

        return new DemoRun(
                database,
                decision.output(),
                refundAttempt.output(),
                approvedRefund.output(),
                replayed.output(),
                db.readOnlyToolInvocationCount(),
                db.effectfulToolInvocationCount(),
                db.refundCount(),
                tokens.modelCalls(),
                tokens.totalTokens(),
                evalReport);
    }

    private static Agent buildAgent(
            ModelPort model,
            OperationsTools tools,
            RecordingObserver recording,
            TokenAccountingObserver tokens,
            boolean allowRefunds) {
        DefaultAgent.Builder builder = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are an operations copilot. Use tools for account, invoice, and policy facts. "
                        + "Never execute money movement without explicit authorization.")
                .tool(tools.accountLookup())
                .tool(tools.invoiceLookup())
                .tool(tools.policyLookup())
                .tool(tools.issueRefund())
                .maxSteps(6)
                .toolTimeout(Duration.ofSeconds(5))
                .maxToolResultChars(2_000);
        if (allowRefunds) {
            builder.toolApprover(ToolApprovers.denyEffectful("issue_refund"));
        }
        if (recording != null) {
            builder.observer(recording);
        }
        if (tokens != null) {
            builder.observer(tokens);
        }
        return builder.build();
    }

    private static final class ScriptedOperationsModel implements ModelPort {
        @Override
        public ModelResponse chat(ModelRequest request) {
            boolean hasToolResults = request.messages().stream().anyMatch(m -> m.role() == Role.TOOL);
            String lastUser = request.messages().stream()
                    .filter(m -> m.role() == Role.USER)
                    .reduce((a, b) -> b)
                    .map(Message::content)
                    .orElse("")
                    .toLowerCase(Locale.ROOT);

            if (!hasToolResults && lastUser.contains("issue")) {
                return new ModelResponse("", List.of(new ToolCall(
                        "refund-1",
                        "issue_refund",
                        "{\"accountId\":\"acct-2042\",\"invoiceId\":\"INV-771\","
                                + "\"amountCents\":3500,"
                                + "\"idempotencyKey\":\"courtesy-acct-2042-inv-771-3500\"}")),
                        new Usage(410, 65));
            }
            if (!hasToolResults) {
                return new ModelResponse("", List.of(
                        new ToolCall("account-1", "account_lookup", "{\"accountId\":\"acct-2042\"}"),
                        new ToolCall("invoice-1", "invoice_lookup", "{\"invoiceId\":\"INV-771\"}"),
                        new ToolCall("policy-1", "policy_lookup", "{\"policy\":\"courtesy-credit\"}")),
                        new Usage(530, 110));
            }
            if (lastUser.contains("issue")) {
                String toolResult = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL)
                        .reduce((a, b) -> b)
                        .map(Message::content)
                        .orElse("");
                if (toolResult.contains("effectful tool")) {
                    return ModelResponse.text(
                            "Refund not issued: the effectful refund tool was denied by policy. "
                                    + "Escalate to a human approver before money movement.",
                            new Usage(390, 95));
                }
                return ModelResponse.text(
                        "Refund issued once using idempotency key courtesy-acct-2042-inv-771-3500. "
                                + "The database transaction recorded the refund for invoice INV-771.",
                        new Usage(390, 95));
            }
            return ModelResponse.text(
                    "Recommendation: approve a $35 courtesy credit, but do not issue it automatically. "
                            + "The SQLite-backed account record shows acct-2042 is in good standing, "
                            + "invoice INV-771 is paid, and the policy allows a one-time courtesy credit "
                            + "up to $50 after a delayed shipment.",
                    new Usage(460, 150));
        }

        @Override
        public String name() {
            return "demo-ops-scripted-model";
        }
    }

    private static final class OperationsTools {
        private static final ObjectMapper MAPPER = new ObjectMapper();

        private final OperationsDatabase database;

        private OperationsTools(OperationsDatabase database) {
            this.database = database;
        }

        Tool accountLookup() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "account_lookup",
                            "Look up account standing and support tier from the operations database.",
                            "{\"type\":\"object\",\"properties\":{\"accountId\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"accountId\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(database.account(text(argumentsJson, "accountId")));
                }
            };
        }

        Tool invoiceLookup() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "invoice_lookup",
                            "Look up invoice status and shipment history from the operations database.",
                            "{\"type\":\"object\",\"properties\":{\"invoiceId\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"invoiceId\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(database.invoice(text(argumentsJson, "invoiceId")));
                }
            };
        }

        Tool policyLookup() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "policy_lookup",
                            "Look up operational policy guidance from the operations database.",
                            "{\"type\":\"object\",\"properties\":{\"policy\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"policy\"]}",
                            ToolEffect.READ_ONLY);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(database.policy(text(argumentsJson, "policy")));
                }
            };
        }

        Tool issueRefund() {
            return new Tool() {
                @Override
                public ToolSpec spec() {
                    return new ToolSpec(
                            "issue_refund",
                            "Issue a refund to a customer account in a database transaction.",
                            "{\"type\":\"object\",\"properties\":{\"accountId\":{\"type\":\"string\"},"
                                    + "\"invoiceId\":{\"type\":\"string\"},"
                                    + "\"amountCents\":{\"type\":\"integer\"},"
                                    + "\"idempotencyKey\":{\"type\":\"string\"}},"
                                    + "\"required\":[\"accountId\",\"invoiceId\",\"amountCents\","
                                    + "\"idempotencyKey\"]}",
                            ToolEffect.EFFECTFUL);
                }

                @Override
                public ToolResult invoke(String argumentsJson) {
                    return ToolResult.ok(database.issueRefund(
                            text(argumentsJson, "accountId"),
                            text(argumentsJson, "invoiceId"),
                            integer(argumentsJson, "amountCents"),
                            text(argumentsJson, "idempotencyKey")));
                }
            };
        }

        private static String text(String argumentsJson, String field) {
            JsonNode node = parse(argumentsJson).get(field);
            if (node == null || node.asText().isBlank()) {
                throw new IllegalArgumentException("missing required field: " + field);
            }
            return node.asText();
        }

        private static int integer(String argumentsJson, String field) {
            JsonNode node = parse(argumentsJson).get(field);
            if (node == null || !node.canConvertToInt()) {
                throw new IllegalArgumentException("missing required integer field: " + field);
            }
            return node.asInt();
        }

        private static JsonNode parse(String argumentsJson) {
            try {
                return MAPPER.readTree(argumentsJson);
            } catch (IOException e) {
                throw new IllegalArgumentException("invalid tool arguments JSON", e);
            }
        }
    }

    private static EvalReport evalReport() {
        Path evalDb = tempDatabase("java-ai-agent-ops-eval-");
        try (OperationsDatabase db = OperationsDatabase.open(evalDb)) {
            return Evaluator.run(buildAgent(new ScriptedOperationsModel(), new OperationsTools(db), null, null, false),
                    List.of(
                            EvalCase.contains(
                                    "courtesy-credit-decision",
                                    "Customer Acme Outdoor Supply asks whether invoice INV-771 qualifies for a courtesy credit.",
                                    "courtesy credit"),
                            EvalCase.contains(
                                    "effectful-refund-denied",
                                    "Issue the $35 courtesy refund for Acme Outdoor Supply now.",
                                    "not issued")));
        }
    }

    private static Path tempDatabase(String prefix) {
        try {
            return FileSystems.getDefault().supportedFileAttributeViews().contains("posix")
                    ? Files.createTempFile(prefix, ".sqlite",
                            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : Files.createTempFile(prefix, ".sqlite");
        } catch (IOException e) {
            throw new IllegalStateException("failed to create demo database", e);
        }
    }
}
