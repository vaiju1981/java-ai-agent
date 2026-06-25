package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Manager} that asks a model what to do next. It shows the task, the roster of specialists,
 * and the transcript so far, then the model either delegates the next step to one specialist or
 * finishes with the final answer.
 *
 * <p>Prefer the {@link StructuredOutput} constructor: the model returns a JSON object bound directly
 * to {@link Move} — no parsing. The {@link ModelPort} constructor is a portable fallback that reads a
 * small line-based reply for providers without structured output. Mirrors {@code LlmPlanner}.
 */
public final class LlmManager implements Manager {

    /** Structured decision the model fills in (bound from JSON, no manual parsing). */
    public record Move(boolean done, String specialist, String instruction, String answer) {
    }

    private final StructuredOutput structured; // preferred; null when using free-text
    private final ModelPort model;             // free-text fallback; null when structured

    public LlmManager(StructuredOutput structured) {
        this.structured = Objects.requireNonNull(structured, "structured");
        this.model = null;
    }

    public LlmManager(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
        this.structured = null;
    }

    @Override
    public Decision decide(String task, List<Round> history, Map<String, String> roster) {
        return structured != null
                ? toDecision(structured.generate(
                        ModelRequest.of(List.of(Message.user(structuredPrompt(task, history, roster)))),
                        Move.class))
                : parse(model.chat(ModelRequest.of(List.of(
                        Message.system("You are a manager coordinating specialists. "
                                + "Reply in the requested form only."),
                        Message.user(freeTextPrompt(task, history, roster))))).text());
    }

    private static Decision toDecision(Move move) {
        if (move == null) {
            return Decision.finish(""); // honest: no decision -> stop rather than loop blindly
        }
        if (move.done() || move.specialist() == null || move.specialist().isBlank()) {
            return Decision.finish(move.answer() == null ? "" : move.answer());
        }
        return Decision.delegate(move.specialist().strip(),
                move.instruction() == null ? "" : move.instruction().strip());
    }

    private static Decision parse(String text) {
        if (text == null || text.isBlank()) {
            return Decision.finish("");
        }
        String trimmed = text.strip();
        String[] lines = trimmed.split("\\R", 2);
        String head = lines[0].strip();
        String rest = lines.length > 1 ? lines[1].strip() : "";
        if (head.equalsIgnoreCase("FINISH") || head.toUpperCase().startsWith("FINISH ")) {
            return Decision.finish(rest);
        }
        if (head.toUpperCase().startsWith("DELEGATE")) {
            return Decision.delegate(head.substring("DELEGATE".length()).strip(), rest);
        }
        // Unrecognized form -> treat the whole reply as a final answer rather than loop blindly.
        return Decision.finish(trimmed);
    }

    private static String structuredPrompt(String task, List<Round> history, Map<String, String> roster) {
        return basePrompt(task, history, roster)
                + "\n\nDecide the next move. If the task is complete and correct, set \"done\": true and "
                + "put the final answer in \"answer\". Otherwise set \"done\": false, name the "
                + "\"specialist\" to act next, and give it a clear \"instruction\".";
    }

    private static String freeTextPrompt(String task, List<Round> history, Map<String, String> roster) {
        return basePrompt(task, history, roster)
                + "\n\nReply in ONE of these two forms and nothing else:\n"
                + "FINISH\n<the final answer>\n"
                + "— or —\n"
                + "DELEGATE <specialist-name>\n<the instruction for that specialist>";
    }

    private static String basePrompt(String task, List<Round> history, Map<String, String> roster) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are the manager of a team of specialists. Coordinate them to complete the task.\n\n");
        sb.append("Task: ").append(task).append("\n\nSpecialists:\n");
        roster.forEach((name, desc) -> sb.append("- ").append(name).append(": ").append(desc).append('\n'));
        if (!history.isEmpty()) {
            sb.append("\nWork so far:\n");
            int i = 1;
            for (Round r : history) {
                sb.append(i++).append(". ").append(r.specialist()).append(" — ").append(r.instruction())
                        .append("\n   result: ").append(r.output()).append('\n');
            }
        }
        return sb.toString();
    }
}
