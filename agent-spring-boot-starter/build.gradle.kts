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

    testImplementation(platform(libs.spring.boot.bom))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.boot.web)
}
