package dev.vaijanath.aiagent.tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A human-in-the-loop {@link ToolApprover}: prompts on the console and permits a tool only on an
 * explicit "y". Useful to gate consequential tool calls behind a person.
 */
public final class ConsoleToolApprover implements ToolApprover {

    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    @Override
    public ToolDecision authorize(String toolName, String argumentsJson) {
        System.out.printf("%n[approval] run tool '%s' with args %s ? [y/N] ", toolName, argumentsJson);
        System.out.flush();
        try {
            String line = in.readLine();
            return (line != null && line.trim().equalsIgnoreCase("y"))
                    ? ToolDecision.allow()
                    : ToolDecision.deny("denied by the user");
        } catch (Exception e) {
            return ToolDecision.deny("approval prompt failed: " + e.getMessage());
        }
    }
}
