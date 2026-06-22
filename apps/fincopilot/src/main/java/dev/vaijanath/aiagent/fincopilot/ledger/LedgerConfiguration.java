package dev.vaijanath.aiagent.fincopilot.ledger;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the JDBC ledger stores. Schema is owned by Flyway (V3). */
@Configuration
class LedgerConfiguration {

    @Bean
    AccountStore accountStore(DataSource dataSource) {
        return new AccountStore(dataSource::getConnection);
    }

    @Bean
    TransactionStore transactionStore(DataSource dataSource) {
        return new TransactionStore(dataSource::getConnection);
    }
}
