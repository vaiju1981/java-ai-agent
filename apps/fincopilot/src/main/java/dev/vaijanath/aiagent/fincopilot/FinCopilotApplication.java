package dev.vaijanath.aiagent.fincopilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the FinCopilot service — the v0.2.0 flagship grounded finance copilot. */
@SpringBootApplication
public class FinCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(FinCopilotApplication.class, args);
    }
}
