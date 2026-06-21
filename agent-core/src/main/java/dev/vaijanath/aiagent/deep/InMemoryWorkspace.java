package dev.vaijanath.aiagent.deep;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** A thread-safe, in-process {@link Workspace}. */
public final class InMemoryWorkspace implements Workspace {

    private final Map<String, String> files = new ConcurrentHashMap<>();

    @Override
    public void write(String path, String content) {
        files.put(path, content);
    }

    @Override
    public Optional<String> read(String path) {
        return Optional.ofNullable(files.get(path));
    }

    @Override
    public Map<String, String> files() {
        return Map.copyOf(files);
    }
}
