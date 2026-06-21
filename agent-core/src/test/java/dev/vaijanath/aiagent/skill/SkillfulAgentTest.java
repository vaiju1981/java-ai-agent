package dev.vaijanath.aiagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class SkillfulAgentTest {

    private static final Tool ADD = new Tool() {
        @Override
        public ToolSpec spec() {
            return new ToolSpec("add", "add numbers", "{\"type\":\"object\",\"properties\":{}}");
        }

        @Override
        public ToolResult invoke(String argumentsJson) {
            return ToolResult.ok("42");
        }
    };

    // Calls the 'add' tool, then echoes the tool's result.
    private static final ModelPort MODEL = new ModelPort() {
        private int calls = 0;

        @Override
        public ModelResponse chat(ModelRequest request) {
            calls++;
            if (calls == 1) {
                return new ModelResponse("", List.of(new ToolCall("c1", "add", "{}")));
            }
            String tool = request.messages().stream()
                    .filter(m -> m.role() == Role.TOOL)
                    .reduce((a, b) -> b)
                    .map(Message::content)
                    .orElse("(none)");
            return ModelResponse.text("answer:" + tool);
        }
    };

    @Test
    void keywordSelectorPicksTheRelevantSkill() {
        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of("math", "add numbers together", "Use the add tool.", List.of(ADD)))
                .register(Skill.of("weather", "report the forecast", "Use the weather tool."));

        List<Skill> selected = new KeywordSkillSelector().select(registry, "please add numbers");

        assertEquals(1, selected.size());
        assertEquals("math", selected.get(0).name());
    }

    @Test
    void selectedSkillsToolBecomesAvailable() {
        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of("math", "add numbers together", "Use the add tool.", List.of(ADD)));

        AgentResponse r = SkillfulAgent.builder()
                .model(MODEL)
                .registry(registry)
                .selector(new KeywordSkillSelector())
                .build()
                .run(new AgentRequest("please add numbers"));

        // "42" proves the skill's tool was registered and executed (else: "unknown tool: add").
        assertTrue(r.output().contains("42"), "got: " + r.output());
    }
}
