package dev.vaijanath.aiagent.reference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

class ConfigurationFailFastTest {

    private static AgentProperties props(String guardModel, List<String> apiKeys) {
        return new AgentProperties(
                null, null, 0, Duration.ofSeconds(90), null, null, null, guardModel, apiKeys, 0, 0);
    }

    private static Environment withProfiles(String... active) {
        StandardEnvironment env = new StandardEnvironment();
        env.setActiveProfiles(active);
        return env;
    }

    private static ApplicationRunner runner(AgentProperties properties, Environment env, String dbPassword) {
        return new AgentConfiguration().configurationWarnings(properties, env, dbPassword);
    }

    @Test
    void failsStartupUnderProdWithInsecureConfig() {
        ApplicationRunner runner = runner(props("", List.of()), withProfiles("prod"), "agent");
        assertThrows(IllegalStateException.class, () -> runner.run(null));
    }

    @Test
    void warnsButStartsInDevWithInsecureConfig() {
        ApplicationRunner runner = runner(props("", List.of()), withProfiles(), "agent");
        assertDoesNotThrow(() -> runner.run(null));
    }

    @Test
    void startsUnderProdWhenSecure() {
        ApplicationRunner runner =
                runner(props("llama-guard3:1b", List.of("k1")), withProfiles("prod"), "strong-password");
        assertDoesNotThrow(() -> runner.run(null));
    }
}
