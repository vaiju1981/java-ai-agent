package dev.vaijanath.aiagent.fincopilot.me;

import dev.vaijanath.aiagent.fincopilot.FinCopilotProperties;
import dev.vaijanath.aiagent.fincopilot.ledger.AccountStore;
import dev.vaijanath.aiagent.fincopilot.ledger.TransactionStore;
import javax.sql.DataSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires usage metering, the data-subject service, and the per-user chat quota filter. */
@Configuration
class MeConfiguration {

    @Bean
    UsageMeter usageMeter(DataSource dataSource) {
        return new UsageMeter(dataSource::getConnection);
    }

    @Bean
    UserDataService userDataService(
            DataSource dataSource, AccountStore accountStore, TransactionStore transactionStore) {
        return new UserDataService(dataSource::getConnection, accountStore, transactionStore);
    }

    @Bean
    FilterRegistrationBean<QuotaFilter> quotaFilter(UsageMeter usageMeter, FinCopilotProperties properties) {
        FilterRegistrationBean<QuotaFilter> registration =
                new FilterRegistrationBean<>(new QuotaFilter(usageMeter, properties.dailyRequestQuota()));
        registration.addUrlPatterns("/api/chat/*"); // after the session auth filter (order 10)
        registration.setOrder(20);
        return registration;
    }
}
