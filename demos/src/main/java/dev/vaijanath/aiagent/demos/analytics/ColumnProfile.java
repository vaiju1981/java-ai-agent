package dev.vaijanath.aiagent.demos.analytics;

import java.util.List;
import java.util.Set;

record ColumnProfile(
        String table,
        String name,
        String physicalType,
        long rows,
        long distinct,
        long nulls,
        Double min,
        Double max,
        Double mean,
        Double stddev,
        List<String> sampleValues,
        Set<String> signals) {}
