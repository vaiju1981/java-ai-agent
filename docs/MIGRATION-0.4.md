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

## Everything else is additive

The tool-fan-out ceiling (`maxToolCallsPerStep`), observer timing, MDC/OTel tracing, `StopReason`,
`AgentResponse.reason()/retryable()`, idempotency (`IdempotencyStore`/`IdempotentAgent`), and per-model
token/cost (`AgentObserver.onUsage`, `TokenAccountingObserver.tokensByModel()`, `TokenPrice`/`Pricing`)
are all new API — existing code keeps compiling and running unchanged.
