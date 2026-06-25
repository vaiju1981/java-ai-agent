package dev.vaijanath.aiagent.a2a;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * A minimal HTTP/1.1 request — method, path, and body — parsed straight off a socket for
 * {@link A2aServer}. Headers are read up to the blank line; the body is read by {@code Content-Length}.
 * Deliberately tiny: it serves the one A2A endpoint (no keep-alive, no chunked encoding).
 */
record RawRequest(String method, String path, String body) {

    private static final int MAX_HEADERS = 64 * 1024;
    private static final int MAX_BODY = 1 << 20; // 1 MiB

    static RawRequest read(InputStream in) throws IOException {
        String headerBlock = readHeaderBlock(in);
        if (headerBlock.isEmpty()) {
            return null;
        }
        String[] lines = headerBlock.split("\r\n");
        String[] requestLine = lines[0].split(" ");
        if (requestLine.length < 2) {
            return null;
        }
        int contentLength = 0;
        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');
            if (colon > 0 && "Content-Length".equalsIgnoreCase(lines[i].substring(0, colon).trim())) {
                contentLength = parseLength(lines[i].substring(colon + 1).trim());
            }
        }
        if (contentLength > MAX_BODY) {
            throw new IOException("A2A request body exceeds " + MAX_BODY + " bytes");
        }
        byte[] body = in.readNBytes(contentLength);
        return new RawRequest(requestLine[0], requestLine[1], new String(body, StandardCharsets.UTF_8));
    }

    private static int parseLength(String value) {
        try {
            return Math.max(0, Integer.parseInt(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String readHeaderBlock(InputStream in) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        int c1 = 0;
        int c2 = 0;
        int c3 = 0;
        int c4 = 0;
        while ((b = in.read()) != -1) {
            buf.write(b);
            c1 = c2;
            c2 = c3;
            c3 = c4;
            c4 = b;
            if (c1 == '\r' && c2 == '\n' && c3 == '\r' && c4 == '\n') {
                break; // end of headers
            }
            if (buf.size() > MAX_HEADERS) {
                throw new IOException("A2A request headers exceed " + MAX_HEADERS + " bytes");
            }
        }
        String raw = buf.toString(StandardCharsets.US_ASCII);
        return raw.endsWith("\r\n\r\n") ? raw.substring(0, raw.length() - 4) : raw.strip();
    }
}
