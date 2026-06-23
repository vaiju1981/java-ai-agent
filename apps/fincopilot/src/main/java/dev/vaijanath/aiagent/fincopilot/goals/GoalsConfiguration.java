package dev.vaijanath.aiagent.fincopilot.goals;

import dev.vaijanath.aiagent.tool.Tool;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the savings-goal store and the effectful set_savings_goal tool (discovered by the starter). */
@Configuration
class GoalsConfiguration {

    @Bean
    SavingsGoalStore savingsGoalStore(DataSource dataSource) {
        // Flyway owns the schema (V5); this performs no DDL.
        return new SavingsGoalStore(dataSource::getConnection);
    }

    @Bean
    Tool setSavingsGoalTool(SavingsGoalStore store) {
        return new SetSavingsGoalTool(store);
    }
}
