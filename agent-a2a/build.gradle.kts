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
