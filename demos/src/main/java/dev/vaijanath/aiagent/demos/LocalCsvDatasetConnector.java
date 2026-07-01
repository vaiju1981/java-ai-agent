package dev.vaijanath.aiagent.demos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

final class LocalCsvDatasetConnector implements AnalyticsDatasetConnector {

    private final Path csv;

    LocalCsvDatasetConnector(Path csv) {
        this.csv = csv;
    }

    @Override
    public DatasetRef load() throws Exception {
        return new DatasetRef(
                "local_csv",
                csv,
                Map.of(
                        "path", csv.toAbsolutePath().toString(),
                        "bytes", Long.toString(Files.size(csv))));
    }
}
