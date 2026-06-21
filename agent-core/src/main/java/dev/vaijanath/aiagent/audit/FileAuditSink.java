package dev.vaijanath.aiagent.audit;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A durable {@link AuditSink} that appends one tab-separated line per event and fsyncs it, so the
 * record survives a crash after {@link #record} returns. Identity fields are sanitized of control
 * characters and the free-text detail is Base64-encoded, so no field can break the line format. Per
 * the {@link AuditSink} contract it never throws into the caller: a write failure is logged and the
 * event dropped.
 *
 * <p>Line format: {@code epochMillis \t eventId \t type \t traceId \t sessionId \t principal \t
 * tenant \t base64(detail)}. Append-only — an audit log is never rewritten. It is not tamper-evident;
 * pair it with OS-level protections or a signing sink if that is required.
 */
public final class FileAuditSink implements AuditSink, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(FileAuditSink.class);

    private final FileOutputStream out;

    public FileAuditSink(Path file) throws IOException {
        this.out = new FileOutputStream(file.toFile(), true); // append
    }

    @Override
    public synchronized void record(AuditEvent event) {
        try {
            out.write(line(event).getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.getFD().sync(); // durable: survives a crash after record() returns
        } catch (IOException e) {
            log.warn("failed to write audit event '{}'; dropping it", event.type(), e);
        }
    }

    private static String line(AuditEvent e) {
        return e.at().toEpochMilli()
                + "\t" + clean(e.eventId())
                + "\t" + clean(e.type())
                + "\t" + clean(e.traceId())
                + "\t" + clean(e.sessionId())
                + "\t" + clean(e.principal())
                + "\t" + clean(e.tenant())
                + "\t" + Base64.getEncoder().encodeToString(
                        (e.detail() == null ? "" : e.detail()).getBytes(StandardCharsets.UTF_8))
                + "\n";
    }

    /** Replace tabs, newlines, and other control chars so an identity field can't break the format. */
    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\p{Cntrl}", " ");
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
