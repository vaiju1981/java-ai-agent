package dev.vaijanath.aiagent.fincopilot.goals;

import dev.vaijanath.aiagent.fincopilot.auth.SessionAuthenticationFilter;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Lists the signed-in user's savings goals (created via the chat agent's approved set_savings_goal tool). */
@RestController
@RequestMapping("/api/goals")
class GoalsController {

    private final SavingsGoalStore store;

    GoalsController(SavingsGoalStore store) {
        this.store = store;
    }

    @GetMapping
    List<GoalView> goals(@RequestAttribute(SessionAuthenticationFilter.PRINCIPAL_ATTRIBUTE) String principal) {
        return store.listByUser(principal).stream()
                .map(g -> new GoalView(
                        g.id(),
                        g.name(),
                        g.targetAmount().toPlainString(),
                        g.targetDate() == null ? null : g.targetDate().toString()))
                .toList();
    }

    record GoalView(String id, String name, String targetAmount, String targetDate) {}
}
