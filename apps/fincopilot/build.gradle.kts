plugins {
    application
}

description = "FinCopilot — a grounded finance copilot built on java-ai-agent (the v0.2.0 flagship app)."

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":agent-core"))
    // Auto-configures the governed agent + streaming factory + executor, and provides the shared
    // AgentTurns / SseAgentObserver web plumbing (so it isn't duplicated per app).
    implementation(project(":agent-spring-boot-starter"))
    implementation(project(":agent-langchain4j")) // Ollama ModelPort
    implementation(project(":agent-store-jdbc")) // durable ConversationStore + its Flyway schema
    implementation(project(":agent-tools-jsonschema"))
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.jdbc)
    implementation(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    runtimeOnly(libs.micrometer.registry.prometheus)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.spring.boot.test)
}

application {
    mainClass.set("dev.vaijanath.aiagent.fincopilot.FinCopilotApplication")
}
