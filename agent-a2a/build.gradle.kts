plugins {
    `java-library`
}

description = "Agent-to-Agent (A2A) — expose an Agent over HTTP and call a remote one as a local Agent."

dependencies {
    api(project(":agent-core"))

    // JSON for the wire contract; the HTTP server (raw java.net sockets) and client
    // (java.net.http) are JDK-only, so the module stays dependency-light.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// No published baseline on Maven Central until this module's first release; enable the API-compat
// check after that lands (see the japicmp config in the root build).
tasks.matching { it.name == "japicmpCheck" }.configureEach { enabled = false }
