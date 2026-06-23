plugins {
    `java-library`
}

description = "Spring Boot starter — autoconfigures a governed Agent from a ModelPort bean."

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
