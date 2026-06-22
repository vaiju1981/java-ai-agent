package dev.vaijanath.aiagent.prompt;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A minimal, dependency-free prompt template with {@code {named}} placeholders, so prompts can be
 * declared once and parameterized per call instead of concatenated by hand. {@link #render(Map)}
 * requires every placeholder to have a value (a missing one is a programming error, not a silent
 * blank), and {@link #variables()} exposes the placeholder names for validation or UI.
 *
 * <p>A placeholder name is letters, digits, {@code _}, {@code .}, or {@code -}; anything else
 * (including {@code { }} with spaces) is treated as literal text.
 */
public final class PromptTemplate {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.\\-]+)\\}");

    private final String template;
    private final Set<String> variables;

    private PromptTemplate(String template) {
        this.template = Objects.requireNonNull(template, "template");
        Set<String> names = new LinkedHashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        this.variables = Set.copyOf(names);
    }

    public static PromptTemplate of(String template) {
        return new PromptTemplate(template);
    }

    /** The distinct placeholder names in this template, in first-seen order. */
    public Set<String> variables() {
        return variables;
    }

    /** Renders the template, substituting every placeholder; throws if any value is missing. */
    public String render(Map<String, String> values) {
        Objects.requireNonNull(values, "values");
        StringBuilder out = new StringBuilder(template.length());
        Matcher matcher = PLACEHOLDER.matcher(template);
        int last = 0;
        while (matcher.find()) {
            out.append(template, last, matcher.start());
            String key = matcher.group(1);
            String value = values.get(key);
            if (value == null) {
                throw new IllegalArgumentException("missing value for placeholder '" + key + "'");
            }
            out.append(value);
            last = matcher.end();
        }
        out.append(template, last, template.length());
        return out.toString();
    }
}
