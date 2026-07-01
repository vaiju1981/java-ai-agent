package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class AnalyticsSemanticLayer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final List<Metric> metrics = List.of(
            new Metric("revenue", "sum(revenue)", "Total revenue from attributed orders.", "money"),
            new Metric("avg_revenue", "avg(revenue)", "Average revenue per row in the selected grain.", "money"),
            new Metric("orders", "sum(orders)", "Total completed orders.", "count"),
            new Metric("conversion_rate", "avg(conversion_rate)", "Average conversion rate.", "rate"),
            new Metric("ad_spend", "sum(ad_spend)", "Total advertising spend.", "money"));

    private final List<Dimension> dimensions = List.of(
            new Dimension("channel", "Marketing acquisition channel."),
            new Dimension("week", "Weekly time grain."),
            new Dimension("discount_rate", "Discount rate applied for the row."),
            new Dimension("repeat_customer_share", "Share of orders from repeat customers."));

    String catalogJson() {
        return json(Map.of(
                "metrics", metrics.stream().map(Metric::asMap).toList(),
                "dimensions", dimensions.stream().map(Dimension::asMap).toList()));
    }

    String planJson(String question) {
        QueryPlan plan = plan(question);
        return json(plan.asMap());
    }

    QueryPlan plan(String question) {
        String q = question.toLowerCase(Locale.ROOT);
        List<String> selectedMetrics = new ArrayList<>();
        if (q.contains("revenue")) {
            selectedMetrics.add(q.contains("average") || q.contains("avg") ? "avg_revenue" : "revenue");
        }
        if (q.contains("conversion")) {
            selectedMetrics.add("conversion_rate");
        }
        if (q.contains("orders")) {
            selectedMetrics.add("orders");
        }
        if (selectedMetrics.isEmpty()) {
            selectedMetrics.add("revenue");
        }

        List<String> selectedDimensions = new ArrayList<>();
        if (q.contains("channel")) {
            selectedDimensions.add("channel");
        }
        if (q.contains("week") || q.contains("time")) {
            selectedDimensions.add("week");
        }

        String sql = sql(selectedMetrics, selectedDimensions);
        return new QueryPlan(
                question,
                selectedMetrics,
                selectedDimensions,
                List.of(),
                selectedDimensions.contains("week") ? "week" : "dataset",
                sql,
                compatible(selectedMetrics, selectedDimensions),
                "Mapped the question to registered metrics and dimensions before producing read-only SQL.");
    }

    private boolean compatible(List<String> selectedMetrics, List<String> selectedDimensions) {
        return selectedMetrics.stream().allMatch(this::hasMetric)
                && selectedDimensions.stream().allMatch(this::hasDimension);
    }

    private boolean hasMetric(String metric) {
        return metrics.stream().anyMatch(m -> m.name().equals(metric));
    }

    private boolean hasDimension(String dimension) {
        return dimensions.stream().anyMatch(d -> d.name().equals(dimension));
    }

    private String sql(List<String> selectedMetrics, List<String> selectedDimensions) {
        List<String> select = new ArrayList<>();
        for (String dimension : selectedDimensions) {
            select.add(dimension);
        }
        for (String metric : selectedMetrics) {
            Metric definition = metrics.stream()
                    .filter(m -> m.name().equals(metric))
                    .findFirst()
                    .orElseThrow();
            select.add("round(" + definition.expression() + ", 3) as " + metric);
        }
        StringBuilder sql = new StringBuilder("select ");
        sql.append(String.join(", ", select));
        sql.append(" from data");
        if (!selectedDimensions.isEmpty()) {
            sql.append(" group by ").append(String.join(", ", selectedDimensions));
            sql.append(" order by ").append(selectedMetrics.getFirst()).append(" desc");
        }
        return sql.toString();
    }

    private record Metric(String name, String expression, String description, String unit) {
        Map<String, Object> asMap() {
            return Map.of(
                    "name", name,
                    "expression", expression,
                    "description", description,
                    "unit", unit);
        }
    }

    private record Dimension(String name, String description) {
        Map<String, Object> asMap() {
            return Map.of("name", name, "description", description);
        }
    }

    record QueryPlan(
            String question,
            List<String> metrics,
            List<String> dimensions,
            List<String> filters,
            String grain,
            String sql,
            boolean compatible,
            String explanation) {
        Map<String, Object> asMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("question", question);
            map.put("metrics", metrics);
            map.put("dimensions", dimensions);
            map.put("filters", filters);
            map.put("grain", grain);
            map.put("sql", sql);
            map.put("compatible", compatible);
            map.put("explanation", explanation);
            return map;
        }
    }

    private static String json(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize semantic layer result", e);
        }
    }
}
