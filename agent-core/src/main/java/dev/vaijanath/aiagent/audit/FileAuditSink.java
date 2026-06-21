package dev.vaijanath.aiagent.audit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;

/**
 * A durable {@link AuditSink} that appends one tab-separated line per event and flushes it
 * immediately, so the record survives a crash after {@link #record} returns. The free-text detail is
 * Base64-encoded so a line never breaks, and the file is append-only (an audit log is not rewritten).
 *
 * <p>Line format: {@code epochMillis \t eventId \t type \t traceId \t sessionId \t principal \t
 * tenant \t base64(detail)}.
 */
public final class FileAuditSink implements AuditSink, AutoCloseable {

    private final BufferedWriter writer;

    public FileAuditSink(Path file) throws IOException {
        this.writer = Files.newBufferedWriter(
                file, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    @Override
    public synchronized void record(AuditEvent event) {
        try {
            writer.write(line(event));
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to write audit event", e);
        }
    }

    private static String line(AuditEvent e) {
        return e.at().toEpochMilli()
                + "\t" + e.eventId()
                + "\t" + e.type()
                + "\t" + e.traceId()
                + "\t" + e.sessionId()
                + "\t" + e.principal()
                + "\t" + e.tenant()
                + "\t" + Base64.getEncoder().encodeToString(
                        (e.detail() == null ? "" : e.detail()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
