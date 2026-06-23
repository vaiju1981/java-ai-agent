package dev.vaijanath.aiagent.springboot;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AgentPropertiesTest {

    @Test
    void acceptsTheValidDefaults() {
        assertThatCode(() -> new AgentProperties().afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void rejectsANonPositiveModelTimeout() {
        AgentProperties props = new AgentProperties();
        props.setModelTimeout(Duration.ZERO);

        assertThatThrownBy(props::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.model-timeout");
    }

    @Test
    void rejectsANegativeToolTimeout() {
        AgentProperties props = new AgentProperties();
        props.setToolTimeout(Duration.ofSeconds(-1));

        assertThatThrownBy(props::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.tool-timeout");
    }

    @Test
    void rejectsAStepBudgetBelowOne() {
        AgentProperties props = new AgentProperties();
        props.setMaxSteps(0);

        assertThatThrownBy(props::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("agent.max-steps");
    }
}
