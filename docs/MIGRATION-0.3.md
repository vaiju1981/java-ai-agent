# Migrating to v0.3.0

**TL;DR — almost all of v0.3.0 is additive. The one breaking change is a new `media` component on
`Message`, and it only affects code that constructs `Message` via its canonical constructor directly
(`new Message(...)`). Code that uses the factory methods (`Message.user(...)`, `Message.assistant(...)`,
etc.) — the documented way — needs no change.**

v0.3.0's headline is **multimodal input**: a message can now carry images (and audio) for vision/audio
models, alongside the multi-agent orchestration set (agents-as-tools, handoffs, group chat, graph),
RAG ingestion, and the A2A protocol.

## Breaking: `Message` gained a `media` component

`Message` is now:

```java
public record Message(
        Role role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId,
        String toolName,
        List<Media> media) { ... }
```

The new `media` component (default `List.of()`) changes the record's **canonical constructor**, so this
is a binary-incompatible change to that one constructor. It is deliberately excluded from the japicmp
API-compatibility gate for this release.

**Who is affected:** only callers that invoke `new Message(role, content, toolCalls, id, name)` directly.

**Fix:** prefer the factory methods (unchanged signatures):

```java
Message.system(text);
Message.user(text);
Message.assistant(text);
Message.assistant(text, toolCalls);
Message.toolResult(toolCallId, toolName, text);
```

If you genuinely need the canonical constructor, add the trailing `List.of()` (or your media list).

## New: multimodal input

Attach images (or audio) to a user turn with the new `Media` type:

```java
import dev.vaijanath.aiagent.model.Media;
import dev.vaijanath.aiagent.model.Message;

Message m = Message.user(
        "What's in this picture?",
        List.of(Media.image("image/png", pngBytes)));      // inline bytes
// also: Media.imageUrl("https://…/cat.png"), Media.imageBase64(mime, b64), Media.audio(mime, bytes)
```

`Media` carries content either inline (base64) or by URL. Whether a part is honored depends on the
model and adapter: the first-party **Anthropic** and **OpenAI** adapters send images to vision models;
text-only models and the other adapters ignore media parts (text still flows normally). A new
`Message.hasMedia()` helper mirrors the existing `hasToolCalls()`.

## Everything else is additive

- **Multi-agent orchestration** — `Agents.asTool`, `HandoffAgent`, `GroupChatAgent`, `GraphAgent`.
- **`agent-openai`** — a first-party `ModelPort` over the official OpenAI Java SDK.
- **`@AiService`-style facade** — `AiServices.create(MyInterface.class, agent)`.
- **RAG ingestion** and the **A2A protocol** — new modules/types; nothing existing changed.

No other types were removed or had stable-seam signatures changed.

## Upgrading

Bump the dependency version to `0.3.0`. If you only used `Message`'s factory methods (the norm), nothing
else is required. If you constructed `Message` via its canonical constructor, add the trailing media
argument (or switch to a factory).
