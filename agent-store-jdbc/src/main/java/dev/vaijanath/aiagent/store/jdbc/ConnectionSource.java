package dev.vaijanath.aiagent.store.jdbc;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Supplies JDBC connections to the store. Implement it over a {@code DataSource}
 * ({@code dataSource::getConnection}) for pooling, or use {@link JdbcConversationStore#fromJdbcUrl}
 * for a simple {@code DriverManager}-backed source.
 */
@FunctionalInterface
public interface ConnectionSource {

    Connection get() throws SQLException;
}
