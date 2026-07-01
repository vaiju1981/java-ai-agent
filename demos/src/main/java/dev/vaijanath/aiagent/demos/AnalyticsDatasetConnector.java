package dev.vaijanath.aiagent.demos;

import java.nio.file.Path;
import java.util.Map;

/**
 * Pluggable source boundary for the analytics demo. Local CSV is implemented for CI; networked sources
 * such as Hugging Face, Kaggle, BigQuery, Snowflake, or Databricks can implement the same seam without
 * changing the analytics tools.
 */
interface AnalyticsDatasetConnector {

    DatasetRef load() throws Exception;

    record DatasetRef(String source, Path localFile, Map<String, String> metadata) {}
}
