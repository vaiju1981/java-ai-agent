plugins {
    application
}

description = "Deployable production reference service for java-ai-agent."

dependencies {
    implementation(platform(libs.spring.boot.bom))
    implementation(project(":agent-core"))
    // Shared AgentTurns / SseAgentObserver web plumbing (its agent auto-config beans are
    // @ConditionalOnMissingBean, so this app's own beans below still take precedence).
    implementation(project(":agent-spring-boot-starter"))
    implementation(project(":agent-langchain4j"))
    implementation(project(":agent-store-jdbc"))
    implementation(project(":agent-tools-jsonschema"))
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.jdbc)
    implementation(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.postgresql)
    runtimeOnly(libs.postgresql)
    // Backs the actuator /prometheus endpoint so agent + JVM metrics are actually scrapeable.
    runtimeOnly(libs.micrometer.registry.prometheus)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.spring.boot.test)
}

application {
    mainClass.set("dev.vaijanath.aiagent.reference.ProductionReferenceApplication")
}
