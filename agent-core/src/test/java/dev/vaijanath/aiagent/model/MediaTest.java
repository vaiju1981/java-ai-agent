package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MediaTest {

    private static final byte[] BYTES = "hello".getBytes(StandardCharsets.UTF_8);
    private static final String B64 = Base64.getEncoder().encodeToString(BYTES);

    @Test
    void imageFromBytesIsBase64EncodedInline() {
        Media m = Media.image("image/png", BYTES);

        assertEquals(Media.Kind.IMAGE, m.kind());
        assertEquals("image/png", m.mimeType());
        assertEquals(B64, m.base64Data());
        assertNull(m.url());
        assertFalse(m.isUrl());
    }

    @Test
    void imageBase64PassesTheDataThrough() {
        Media m = Media.imageBase64("image/jpeg", B64);

        assertEquals(Media.Kind.IMAGE, m.kind());
        assertEquals(B64, m.base64Data());
        assertFalse(m.isUrl());
    }

    @Test
    void imageUrlIsCarriedByReference() {
        Media m = Media.imageUrl("https://example.com/cat.png");

        assertEquals(Media.Kind.IMAGE, m.kind());
        assertEquals("https://example.com/cat.png", m.url());
        assertNull(m.base64Data());
        assertTrue(m.isUrl());
    }

    @Test
    void audioFactories() {
        Media fromBytes = Media.audio("audio/wav", BYTES);
        Media fromB64 = Media.audioBase64("audio/wav", B64);

        assertEquals(Media.Kind.AUDIO, fromBytes.kind());
        assertEquals(B64, fromBytes.base64Data());
        assertEquals(Media.Kind.AUDIO, fromB64.kind());
        assertEquals(B64, fromB64.base64Data());
    }

    @Test
    void rejectsAPartWithNeitherDataNorUrl() {
        assertThrows(IllegalArgumentException.class, () -> new Media(Media.Kind.IMAGE, "image/png", null, null));
    }

    @Test
    void rejectsNullKindOrNullBytes() {
        assertThrows(NullPointerException.class, () -> new Media(null, "image/png", "x", null));
        assertThrows(NullPointerException.class, () -> Media.image("image/png", null));
        assertThrows(NullPointerException.class, () -> Media.audio("audio/wav", null));
    }
}
