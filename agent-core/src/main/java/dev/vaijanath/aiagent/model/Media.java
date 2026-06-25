package dev.vaijanath.aiagent.model;

import java.util.Base64;
import java.util.Objects;

/**
 * A non-text part of a message — an image or audio clip — for multimodal models. It carries the bytes
 * either inline as base64 ({@link #base64Data} with its {@link #mimeType}) or by reference as a
 * {@link #url}. Build one with a factory and attach it to a user turn via
 * {@link Message#user(String, java.util.List)}:
 *
 * <pre>{@code
 * Message m = Message.user(
 *         "What's in this picture?", List.of(Media.image("image/png", pngBytes)));
 * }</pre>
 *
 * <p>Whether a part is honored depends on the model and adapter: the first-party Anthropic and OpenAI
 * adapters send images to vision models; text-only models and adapters simply ignore media parts.
 */
public record Media(Kind kind, String mimeType, String base64Data, String url) {

    /** The kind of content a part carries. */
    public enum Kind {
        IMAGE,
        AUDIO
    }

    public Media {
        Objects.requireNonNull(kind, "kind");
        if (base64Data == null && url == null) {
            throw new IllegalArgumentException("media needs either base64Data or a url");
        }
    }

    /** An image from raw bytes (base64-encoded inline); {@code mimeType} e.g. {@code "image/png"}. */
    public static Media image(String mimeType, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new Media(Kind.IMAGE, mimeType, Base64.getEncoder().encodeToString(bytes), null);
    }

    /** An image already base64-encoded. */
    public static Media imageBase64(String mimeType, String base64) {
        return new Media(Kind.IMAGE, mimeType, base64, null);
    }

    /** An image referenced by URL (the model fetches it). */
    public static Media imageUrl(String url) {
        return new Media(Kind.IMAGE, null, null, url);
    }

    /** An audio clip from raw bytes (base64-encoded inline); {@code mimeType} e.g. {@code "audio/wav"}. */
    public static Media audio(String mimeType, byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        return new Media(Kind.AUDIO, mimeType, Base64.getEncoder().encodeToString(bytes), null);
    }

    /** An audio clip already base64-encoded. */
    public static Media audioBase64(String mimeType, String base64) {
        return new Media(Kind.AUDIO, mimeType, base64, null);
    }

    /** True when this part is carried by reference (a {@link #url}) rather than inline bytes. */
    public boolean isUrl() {
        return url != null;
    }
}
