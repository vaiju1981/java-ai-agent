plugins {
    `java-library`
}

description = "Spring Boot starter — autoconfigures a governed Agent from a ModelPort bean."

// AgentAutoConfiguration is @Internal Spring plumbing: its @Bean method signatures (Spring calls them
// reflectively and they evolve to inject new optional beans) are not a user-facing API, and its behaviour
// is covered by the autoconfiguration tests. Exclude it from the API-compatibility check by name (the
// @Internal annotation was added after the 0.1.2 baseline, so annotation-exclude alone can't match it).
extra["japicmpExcludes"] = "dev.vaijanath.aiagent.springboot.AgentAutoConfiguration"

dependencies {
    api(project(":agent-core"))
    // The default tool-argument validator the governed runtime requires.
    api(project(":agent-tools-jsonschema"))

    implementation(platform(libs.spring.boot.bom))
    implementation(libs.spring.boot.autoconfigure)
    // Optional web helpers (SseAgentObserver, AgentTurns) for agent HTTP apps; consumers that use them
    // already have Spring MVC on the classpath, so it stays compile-only here (no forced dependency).
    compileOnly(libs.spring.boot.web)
    // Optional metrics: the Micrometer agent observer autoconfigures only when a MeterRegistry is on the
    // classpath (e.g. via spring-boot-starter-actuator), so Micrometer stays compile-only here.
    compileOnly(libs.micrometer.core)
    // Optional health: ModelEndpointHealthIndicator uses Actuator's health API; consumers that register
    // it already have Actuator on the classpath, so it stays compile-only here (no forced dependency).
    compileOnly(libs.spring.boot.actuator)

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.boot.web)
    testImplementation(libs.micrometer.core)
    testImplementation(libs.spring.boot.actuator)
}
