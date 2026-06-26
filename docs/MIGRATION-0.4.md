# Migrating to v0.4.0

**TL;DR — v0.4.0 is a hardening release and almost entirely additive. The one library breaking change is
a new `deadlineEpochMillis` component on `A2aRequest`, and it only affects code that constructs
`A2aRequest` via its canonical constructor directly. Code that uses `RemoteAgent` / `A2aServer` (the
documented way) or the `A2aRequest.of(...)` factory needs no change.**

v0.4.0's theme is production rigor and observability: a tool-fan-out ceiling, observer timing + tracing,
an error taxonomy, turn-level idempotency, per-model token/cost accounting, and a hardened
`production-reference`.

## Breaking: `A2aRequest` gained a `deadlineEpochMillis` component

`A2aRequest` is now:

```java
public record A2aRequest(
        String input,
        String sessionId,
        String principal,
        String tenant,
        String traceId,
        Long deadlineEpochMillis) { ... }
```

The new `deadlineEpochMillis` component carries the caller's remaining deadline across the network hop, so
the remote turn is bounded server-side instead of running on after the caller has timed out. It changes
the record's **canonical constructor**, so this is a binary-incompatible change to that one constructor.
It is deliberately excluded from the japicmp API-compatibility gate for this release.

**Who is affected:** only callers that invoke `new A2aRequest(input, sessionId, principal, tenant, traceId)`
directly. The supported paths are unaffected:

- `RemoteAgent` builds the request for you (and now fills in the deadline from the `RequestContext`).
- `A2aServer` maps the request to the remote turn's `RequestContext`.

**Fix:** prefer the factory, or pass the new argument (`null` preserves the prior behavior):

```java
A2aRequest.of(input);                                            // identity/session/deadline default
new A2aRequest(input, sessionId, principal, tenant, traceId, null); // explicit, no deadline
```

## Not breaking, but worth knowing if you based a deployment on `production-reference`

`production-reference` is a reference app, not a published artifact — but if you copied its configuration,
note these changes:

- **`agent.api-keys` is now a map** (`key → tenant`) instead of a list, so each credential is bound to a
  tenant. The `X-Tenant-Id` header is no longer trusted when authentication is enabled; the tenant comes
  from the authenticated key.
- **The rate limiter requires `Content-Length`** on requests to `/api/*` (returns `411 Length Required`
  otherwise) and is keyed by credential/address.
- **Unsafe configuration fails startup under the `prod` profile** (missing API keys, the default DB
  password, or no guard model) instead of only logging a warning.
- **Turn idempotency is enforced**: send an `Idempotency-Key` header and a retried request replays the
  prior result. This needs the new `V2__agent_idempotency.sql` Flyway migration (shipped in
  `agent-store-jdbc`).

## Heads-up: `agent-store-jdbc` adds a second Flyway migration

`agent-store-jdbc` ships its schema as Flyway migrations under the default `classpath:db/migration`. In
0.3.0 it shipped only `V1__agent_conversation_store.sql`; **0.4.0 adds `V2__agent_idempotency.sql`** (for
the durable `JdbcIdempotencyStore`).

If your application has **its own** Flyway migrations under `db/migration` and you numbered them starting
at `V2` (reserving `V1` for the library), they now **collide** with the library's `V2` — Flyway fails at
startup with *"Found more than one migration with version 2"*. Flyway shares one global version space
across all locations, and it scans `db/migration` **recursively**, so a subfolder does not help.

**Recommended fix — let your app own its full schema in a separate location:**

1. Move your migrations to a location that is **not** under `db/migration`, e.g. `classpath:db/<yourapp>`
   (a sibling, not a subfolder).
2. If you use `JdbcConversationStore` (or `JdbcIdempotencyStore`), copy the DDL you need from
   `agent-store-jdbc`'s migrations into your own first migration there — keeping the same filename and
   contents preserves the checksum so existing `flyway_schema_history` still validates.
3. Point Flyway at only your location: `spring.flyway.locations=classpath:db/<yourapp>`. The library's
   `db/migration` is then never scanned, so your version space is independent of future library migrations.
4. If you build Flyway programmatically (e.g. in tests), set `.locations("classpath:db/<yourapp>")` too.

A worked example is the FinCopilot bump:
[vaiju1981/fincopilot#1](https://github.com/vaiju1981/fincopilot/pull/1).

## Everything else is additive

The tool-fan-out ceiling (`maxToolCallsPerStep`), observer timing, MDC/OTel tracing, `StopReason`,
`AgentResponse.reason()/retryable()`, idempotency (`IdempotencyStore`/`IdempotentAgent`), and per-model
token/cost (`AgentObserver.onUsage`, `TokenAccountingObserver.tokensByModel()`, `TokenPrice`/`Pricing`)
are all new API — existing code keeps compiling and running unchanged.
