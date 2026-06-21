package dev.vaijanath.aiagent.eval;

import java.util.List;

/** Aggregated results of an evaluation run. */
public record EvalReport(List<EvalResult> results) {

    public EvalReport {
        results = List.copyOf(results);
    }

    public long passed() {
        return results.stream().filter(EvalResult::passed).count();
    }

    public int total() {
        return results.size();
    }

    public double passRate() {
        return total() == 0 ? 0.0 : (double) passed() / total();
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("eval: %d/%d passed (%.0f%%)%n", passed(), total(), passRate() * 100));
        for (EvalResult r : results) {
            sb.append(r.passed() ? "  PASS  " : "  FAIL  ").append(r.name()).append('\n');
        }
        return sb.toString();
    }
}
