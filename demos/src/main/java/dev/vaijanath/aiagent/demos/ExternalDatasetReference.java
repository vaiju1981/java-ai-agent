package dev.vaijanath.aiagent.demos;

import java.nio.file.Path;
import java.util.Map;

/**
 * Documents an external dataset target without downloading it. This keeps CI deterministic while making
 * the large-dataset extension path explicit.
 */
record ExternalDatasetReference(String provider, String id, String url, String recommendedUse) {

    AnalyticsDatasetConnector.DatasetRef asMetadataOnlyRef() {
        return new AnalyticsDatasetConnector.DatasetRef(
                provider + ":" + id,
                Path.of(""),
                Map.of("url", url, "recommendedUse", recommendedUse));
    }
}
