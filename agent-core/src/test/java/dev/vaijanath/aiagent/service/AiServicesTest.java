package dev.vaijanath.aiagent.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import org.junit.jupiter.api.Test;

class AiServicesTest {

    interface Assistant {
        String chat(String message);

        @UserMessage("Summarize in {{words}} words:\n{{text}}")
        String summarize(@V("text") String text, @V("words") int words);

        @UserMessage("Translate to {{lang}}: {{text}}")
        String translate(@V("lang") String lang, @V("text") String text, String ignoredNoAnnotation);

        @UserMessage("ping")
        String ping();

        AgentResponse raw(String message);

        int badReturn(String x);

        String twoArgs(String a, String b);

        default String greeting() {
            return "hi from default";
        }
    }

    /** Records the last input it saw and how many times it was called. */
    private static final class CapturingAgent implements Agent {
        volatile String lastInput;
        volatile int calls;

        @Override
        public AgentResponse run(AgentRequest request) {
            lastInput = request.input();
            calls++;
            return AgentResponse.completed("ECHO:" + request.input());
        }
    }

    @Test
    void singleArgMethodPassesTheArgumentAsInput() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        String result = a.chat("hello");

        assertEquals("hello", agent.lastInput);
        assertEquals("ECHO:hello", result);
    }

    @Test
    void rendersUserMessageTemplateFromAnnotatedParams() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        a.summarize("the quick brown fox", 5);

        assertEquals("Summarize in 5 words:\nthe quick brown fox", agent.lastInput);
    }

    @Test
    void templateIgnoresParamsWithoutAnnotation() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        a.translate("French", "good morning", "this one is not in the template");

        assertEquals("Translate to French: good morning", agent.lastInput);
    }

    @Test
    void zeroArgTemplateMethodUsesTheTemplateVerbatim() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        a.ping();

        assertEquals("ping", agent.lastInput);
    }

    @Test
    void returnsTheFullAgentResponseWhenDeclared() {
        Assistant a = AiServices.create(Assistant.class, new CapturingAgent());

        AgentResponse response = a.raw("hi");

        assertEquals("ECHO:hi", response.output());
        assertTrue(response.isCompleted());
    }

    @Test
    void unsupportedReturnTypeThrowsAndDoesNotCallTheAgent() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        assertThrows(IllegalStateException.class, () -> a.badReturn("x"));
        assertEquals(0, agent.calls);
    }

    @Test
    void multipleArgsWithoutTemplateThrows() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        assertThrows(IllegalStateException.class, () -> a.twoArgs("a", "b"));
        assertEquals(0, agent.calls);
    }

    @Test
    void defaultMethodRunsWithoutCallingTheAgent() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        assertEquals("hi from default", a.greeting());
        assertEquals(0, agent.calls);
    }

    @Test
    void objectMethodsDoNotCallTheAgent() {
        CapturingAgent agent = new CapturingAgent();
        Assistant a = AiServices.create(Assistant.class, agent);

        assertTrue(a.toString().contains("AiService"));
        assertEquals(a, a); // reflexive equals
        a.hashCode(); // does not throw
        assertEquals(0, agent.calls);
    }

    @Test
    void createRejectsANonInterface() {
        assertThrows(IllegalArgumentException.class,
                () -> AiServices.create(String.class, new CapturingAgent()));
    }
}
