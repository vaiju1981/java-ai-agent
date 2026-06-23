package dev.vaijanath.aiagent.fincopilot.analyst;

import dev.vaijanath.aiagent.fincopilot.ledger.TransactionStore;
import dev.vaijanath.aiagent.tool.Tool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Analyst's analytics and READ_ONLY tools. The agent-spring-boot-starter discovers these
 * {@link Tool} beans and adds them to the governed agent (sync and streaming), so the model can call
 * them; each tool scopes to the authenticated user via the invocation context.
 */
@Configuration
class AnalystConfiguration {

    @Bean
    Analytics analytics(TransactionStore transactionStore) {
        return new Analytics(transactionStore);
    }

    @Bean
    Tool financeSummaryTool(Analytics analytics) {
        return new AnalystTools.FinanceSummaryTool(analytics);
    }

    @Bean
    Tool spendingByCategoryTool(Analytics analytics) {
        return new AnalystTools.SpendingByCategoryTool(analytics);
    }

    @Bean
    Tool monthlyCashflowTool(Analytics analytics) {
        return new AnalystTools.MonthlyCashflowTool(analytics);
    }
}
