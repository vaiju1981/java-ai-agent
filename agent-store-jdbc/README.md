# agent-store-jdbc

A durable, **queryable** `ConversationStore` backed by SQLite or PostgreSQL — so conversations survive
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

- **SQLite and PostgreSQL** use the same schema. `fromJdbcUrl` is a local-development convenience
  that initializes it automatically. In a service, run the bundled migration and pass a pooled
  `DataSource`: `new JdbcConversationStore(dataSource::getConnection)`; this constructor never
  performs application-startup DDL.
- **Turn-aware windowing:** `fromJdbcUrl(url, maxTurns)` always includes system messages and the most
  recent N complete turns while still persisting full history for analytics.
- **Atomic turns:** messages produced by one agent turn commit in one transaction; failed turns leave
  no partial tool-call history.
- **Migrations:** the Flyway-compatible schema is bundled at
  `classpath:db/migration/V1__agent_conversation_store.sql`.
- **Faithful:** tool calls and tool results are persisted, so tool-using conversations resume exactly.
- **Concurrency:** same-session work is serialized in-process; for multiple instances writing the same
  session, use sticky routing or external coordination. A clash rolls back the complete turn and
  throws `ConcurrentConversationException`, which an HTTP service should expose as `409 Conflict`.
