package dev.vaijanath.aiagent.springboot;

import static org.assertj.core.api.Assertions.assertThat;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.audit.AuditSink;
import dev.vaijanath.aiagent.memory.ConversationStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentAutoConfigurationTest {

    private static final ModelPort STUB_MODEL = request -> ModelResponse.text("ok");

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentAutoConfiguration.class));

    @Test
    void doesNotBuildAnAgentWithoutAModelPort() {
        runner.run(context -> assertThat(context).doesNotHaveBean(Agent.class));
    }

    @Test
    void buildsAGovernedAgentWithDefaultsWhenAModelPortIsPresent() {
        runner.withBean("model", ModelPort.class, () -> STUB_MODEL).run(context -> {
            assertThat(context).hasSingleBean(Agent.class);
            assertThat(context).hasSingleBean(ConversationStore.class);
            assertThat(context).hasSingleBean(AuditSink.class);
        });
    }

    @Test
    void bindsConfigurationProperties() {
        runner.withBean("model", ModelPort.class, () -> STUB_MODEL)
                .withPropertyValues("agent.system-prompt=be terse", "agent.max-steps=3", "agent.model-timeout=10s")
                .run(context -> {
                    AgentProperties props = context.getBean(AgentProperties.class);
                    assertThat(props.getSystemPrompt()).isEqualTo("be terse");
                    assertThat(props.getMaxSteps()).isEqualTo(3);
                    assertThat(context).hasSingleBean(Agent.class);
                });
    }

    @Test
    void backsOffWhenTheApplicationDefinesItsOwnAgent() {
        Agent custom = request -> AgentResponse.completed("custom");
        runner.withBean("model", ModelPort.class, () -> STUB_MODEL)
                .withBean("customAgent", Agent.class, () -> custom)
                .run(context -> assertThat(context.getBean(Agent.class).run(new AgentRequest("x")).output())
                        .isEqualTo("custom"));
    }
}
