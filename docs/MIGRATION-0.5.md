# Migrating to v0.5.0

**TL;DR — one breaking change: `agent-store-jdbc` no longer ships its Flyway migrations in the default
`classpath:db/migration`. They now live under `classpath:db/agent-store-jdbc/`. If you relied on the
library's migrations being auto-discovered in `db/migration`, add the new location to
`spring.flyway.locations`. If you use `fromJdbcUrl(...)`, nothing changes.**

## Why

`agent-store-jdbc` shipped its schema as Flyway migrations in the **default** `classpath:db/migration`.
Flyway shares one global version space across all locations, so the library's `V1`/`V2`(/`V3` in 0.5.0)
**collided** with any application that numbered its own migrations in the same space — a real failure when
a 0.3.0→0.4.0 bump introduced the library's `V2` (it clashed with a consumer's `V2`). Shipping migrations
into the consumer's default location was the root cause.

## What changed

The library's migrations moved to a **namespaced, opt-in** location:

| | before (≤ 0.4.0) | now (0.5.0) |
|---|---|---|
| location | `classpath:db/migration/` | `classpath:db/agent-store-jdbc/` |
| applied | automatically (default location) | only if you add the location |

The migration **files, versions, and contents are unchanged** (`V1__agent_conversation_store`,
`V2__agent_idempotency`, `V3__agent_episodes`), so an existing database's `flyway_schema_history` still
validates once Flyway points at the new location (the recorded script names match).

## What you need to do

- **If you let Flyway auto-discover the library's migrations from `classpath:db/migration`:** add the new
  location to your configuration:
  ```properties
  spring.flyway.locations=classpath:db/agent-store-jdbc,classpath:db/migration
  ```
  (List your own app migrations' location too — now there's no version-space collision with the library.)
- **If you already own your full schema** (copied the DDL into your own location, as the FinCopilot
  reference app does): nothing to do — you were never depending on the library's location.
- **If you use `JdbcConversationStore.fromJdbcUrl(...)` / `JdbcEpisodicStore.fromJdbcUrl(...)`** for local
  development: nothing changes — those still self-create the schema.

## Also in 0.5.0 (additive)

- `JdbcEpisodicStore` — durable, semantic episodic memory (embeddings + cosine recall) for the
  self-learning `ReflectiveAgent`; survives restarts and is shared across instances.
