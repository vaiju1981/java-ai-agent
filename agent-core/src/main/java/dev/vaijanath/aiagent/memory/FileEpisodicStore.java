package dev.vaijanath.aiagent.memory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A persistent {@link EpisodicStore} — episodes are appended to a file and reloaded on construction,
 * so an agent's learnings survive a process restart and apply in a <b>new session</b>.
 *
 * <p>Dep-free: each episode is one line of base64-encoded, tab-separated fields (so any text,
 * including newlines, round-trips). Use one file per user/agent to scope memory. Recall uses the
 * same keyword relevance as {@link InMemoryEpisodicStore}.
 */
public final class FileEpisodicStore implements EpisodicStore {

    private static final java.nio.charset.Charset UTF8 = StandardCharsets.UTF_8;

    private final Path file;
    private final List<Episode> episodes = new CopyOnWriteArrayList<>();

    public FileEpisodicStore(Path file) {
        this.file = file;
        load();
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(file, UTF8)) {
                if (!line.isBlank()) {
                    Episode e = decode(line);
                    if (e != null) {
                        episodes.add(e);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load episodes from " + file, e);
        }
    }

    @Override
    public synchronized void record(Episode episode) {
        episodes.add(episode);
        try {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, encode(episode) + System.lineSeparator(), UTF8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to persist episode to " + file, e);
        }
    }

    @Override
    public List<Episode> recall(String query, int limit) {
        return Episodes.recall(List.copyOf(episodes), query, limit);
    }

    private static String encode(Episode e) {
        return String.join("\t",
                b64(e.task()), b64(e.outcome()), Boolean.toString(e.success()), b64(e.lesson()));
    }

    private static Episode decode(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 4) {
            return null;
        }
        return new Episode(unb64(parts[0]), unb64(parts[1]), Boolean.parseBoolean(parts[2]), unb64(parts[3]));
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(UTF8));
    }

    private static String unb64(String s) {
        return new String(Base64.getDecoder().decode(s), UTF8);
    }
}
