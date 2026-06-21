# agent-store-jdbc

A durable, **queryable** `ConversationStore` backed by a SQL database — so conversations survive
restarts and can be analysed with plain SQL, unlike the in-memory store (lost on restart) or an
opaque file store.

```java
ConversationStore store = JdbcConversationStore.fromJdbcUrl("jdbc:sqlite:conversations.db");
Agent agent = DefaultAgent.builder().model(model).conversationStore(store).build();
```

Each message is one row in `agent_messages` (`tenant, session_id, seq, role, content, tool_call_id,
tool_name, tool_calls, created_at`), so analytics is just SQL:

```sql
SELECT tenant, role, COUNT(*) FROM agent_messages GROUP BY tenant, role;
SELECT session_id, MAX(created_at) - MIN(created_at) AS span_ms FROM agent_messages GROUP BY session_id;
```

- **Any JDBC driver** on the classpath works (SQLite, PostgreSQL, MySQL, …). For pooling, pass a
  `DataSource`: `new JdbcConversationStore(dataSource::getConnection)`.
- **Windowing:** `fromJdbcUrl(url, maxMessages)` replays only the most recent N messages to the model
  while still persisting the full history for analytics.
- **Faithful:** tool calls and tool results are persisted, so tool-using conversations resume exactly.
- **Concurrency:** same-session work is serialized in-process; for multiple instances writing the same
  session, use sticky routing or external coordination (a clash is a loud primary-key conflict, never
  silent corruption).
