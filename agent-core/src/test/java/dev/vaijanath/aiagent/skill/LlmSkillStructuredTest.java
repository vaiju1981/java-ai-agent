package dev.vaijanath.aiagent.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmSkillStructuredTest {

    @Test
    void selectorUsesStructuredSelection() {
        SkillRegistry registry = new SkillRegistry()
                .register(Skill.of("math", "math", "do math"))
                .register(Skill.of("poet", "poems", "write poems"));
        StructuredOutput structured = new StructuredOutput() {
            @Override
            public <T> T generate(ModelRequest request, Class<T> type) {
                return type.cast(new LlmSkillSelector.Selection(List.of("math")));
            }
        };

        List<Skill> selected = new LlmSkillSelector(structured).select(registry, "do some math");

        assertEquals(1, selected.size());
        assertEquals("math", selected.get(0).name());
    }

    @Test
    void synthesizerUsesStructuredSkill() {
        StructuredOutput structured = new StructuredOutput() {
            @Override
            public <T> T generate(ModelRequest request, Class<T> type) {
                return type.cast(new LlmSkillSynthesizer.SkillDto("solver", "solves things", "do it stepwise"));
            }
        };

        Skill s = new LlmSkillSynthesizer(structured).synthesize("a task", "a solution");

        assertNotNull(s);
        assertEquals("solver", s.name());
        assertEquals("do it stepwise", s.instructions());
    }
}
