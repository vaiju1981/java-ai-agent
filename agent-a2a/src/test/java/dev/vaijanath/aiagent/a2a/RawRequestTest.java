package dev.vaijanath.aiagent.a2a;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RawRequestTest {

    private static RawRequest read(String raw) throws IOException {
        return RawRequest.read(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void parsesMethodPathAndBody() throws Exception {
        RawRequest r = read("POST /run HTTP/1.1\r\nContent-Length: 5\r\nHost: x\r\n\r\nhello");

        assertEquals("POST", r.method());
        assertEquals("/run", r.path());
        assertEquals("hello", r.body());
    }

    @Test
    void missingContentLengthMeansEmptyBody() throws Exception {
        RawRequest r = read("GET / HTTP/1.1\r\nHost: x\r\n\r\n");

        assertEquals("GET", r.method());
        assertEquals("", r.body());
    }

    @Test
    void emptyStreamIsNull() throws Exception {
        assertNull(read(""));
    }

    @Test
    void rejectsAnOversizedBody() {
        assertThrows(
                IOException.class, () -> read("POST / HTTP/1.1\r\nContent-Length: 2000000\r\n\r\n"));
    }

    @Test
    void returnsNullForAGarbledRequestLine() throws Exception {
        assertNull(read("garbage\r\n\r\n"));
    }

    @Test
    void treatsNonNumericContentLengthAsEmptyBody() throws Exception {
        RawRequest r = read("POST / HTTP/1.1\r\nContent-Length: abc\r\n\r\n");

        assertEquals("", r.body());
    }
}
