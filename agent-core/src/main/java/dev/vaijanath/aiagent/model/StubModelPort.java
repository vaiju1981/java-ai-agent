package dev.vaijanath.aiagent.model;

/**
 * An honest placeholder model, used when no real model is configured.
 *
 * <p>It never fabricates an answer: it clearly marks itself as a stub and echoes the last user
 * message, so a missing configuration is obvious rather than silently faked. This embodies the
 * project's "never fake success silently" principle.
 */
public final class StubModelPort implements ModelPort {

    public static final String PREFIX =
            "[stub-model] no real model configured; echoing your message: ";

    @Override
    public ModelResponse chat(ModelRequest request) {
        String lastUser = request.messages().stream()
                .filter(m -> m.role() == Role.USER)
                .reduce((a, b) -> b)
                .map(Message::content)
                .orElse("(no user message)");
        return ModelResponse.text(PREFIX + lastUser);
    }

    @Override
    public String name() {
        return "stub";
    }
}
