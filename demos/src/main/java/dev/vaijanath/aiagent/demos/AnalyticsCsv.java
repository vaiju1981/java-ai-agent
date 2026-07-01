package dev.vaijanath.aiagent.demos;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class AnalyticsCsv {

    private AnalyticsCsv() {}

    static List<List<String>> read(Path csv) {
        try (BufferedReader reader = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            List<List<String>> rows = new ArrayList<>();
            StringBuilder record = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!record.isEmpty()) {
                    record.append('\n');
                }
                record.append(line);
                if (balancedQuotes(record)) {
                    rows.add(parseRecord(record.toString()));
                    record.setLength(0);
                }
            }
            if (!record.isEmpty()) {
                rows.add(parseRecord(record.toString()));
            }
            return rows;
        } catch (IOException e) {
            throw new IllegalStateException("failed to read CSV " + csv, e);
        }
    }

    private static boolean balancedQuotes(CharSequence s) {
        boolean inQuotes = false;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '"') {
                if (inQuotes && i + 1 < s.length() && s.charAt(i + 1) == '"') {
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            }
        }
        return !inQuotes;
    }

    private static List<String> parseRecord(String record) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < record.length() && record.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(field.toString().trim());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString().trim());
        return fields;
    }
}
