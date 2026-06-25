plugins {
    `java-library`
}

description = "Agent-to-Agent (A2A) — expose an Agent over HTTP and call a remote one as a local Agent."

// A2aRequest gained a `deadlineEpochMillis` component (propagate the caller's deadline across the hop).
// That is a deliberate binary-incompatible change to a wire DTO's canonical constructor — exclude it from
// the japicmp guard for this release; remove once the baseline is bumped after release.
extra["japicmpExcludes"] = "dev.vaijanath.aiagent.a2a.A2aRequest"

dependencies {
    api(project(":agent-core"))

    // JSON for the wire contract; the HTTP server (raw java.net sockets) and client
    // (java.net.http) are JDK-only, so the module stays dependency-light.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
