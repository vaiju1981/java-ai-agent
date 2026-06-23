package dev.vaijanath.aiagent.fincopilot.ledger;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses transaction CSV with the header {@code date,amount,merchant,category,description} (a leading
 * header row is detected and skipped). {@code date} is ISO ({@code yyyy-MM-dd}); {@code amount} is a
 * signed decimal (positive = income, negative = expense). Simple double-quoted fields are supported.
 */
public final class CsvTransactions {

    private static final int MAX_ROWS = 10_000;

    private CsvTransactions() {}

    /** A parsed CSV row (without the identity fields the server assigns: id, user, account). */
    public record Row(
            LocalDate date, BigDecimal amount, String merchant, String category, String description) {}

    public static List<Row> parse(String csv) {
        List<Row> rows = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return rows;
        }
        String[] lines = csv.strip().split("\\r?\\n");
        int start = lines.length > 0 && lines[0].toLowerCase(Locale.ROOT).contains("date") ? 1 : 0;
        if (lines.length - start > MAX_ROWS) {
            throw new IllegalArgumentException("too many rows (max " + MAX_ROWS + ")");
        }
        for (int i = start; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty()) {
                continue;
            }
            String[] f = split(line);
            if (f.length < 2) {
                throw new IllegalArgumentException("line " + (i + 1) + ": expected at least date,amount");
            }
            try {
                rows.add(new Row(
                        LocalDate.parse(f[0].strip()),
                        new BigDecimal(f[1].strip()),
                        field(f, 2, ""),
                        blankTo(field(f, 3, ""), "uncategorized"),
                        field(f, 4, "")));
            } catch (DateTimeParseException | NumberFormatException e) {
                throw new IllegalArgumentException("line " + (i + 1) + ": " + e.getMessage());
            }
        }
        return rows;
    }

    private static String field(String[] fields, int index, String fallback) {
        return index < fields.length ? fields[index].strip() : fallback;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /** Splits one CSV line on commas, honouring simple double-quoted fields. */
    private static String[] split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (c == ',' && !quoted) {
                out.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        out.add(field.toString());
        return out.toArray(new String[0]);
    }
}
