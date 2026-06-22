plugins {
    `java-library`
}

description = "Direct Anthropic ModelPort — a ModelPort backed by the official Anthropic Java SDK."

dependencies {
    api(project(":agent-core"))

    // The official Anthropic Java SDK — talks to the Messages API directly, no intermediary framework.
    implementation(libs.anthropic.java)
    // Used to bridge our JSON-schema tool specs and tool arguments to the SDK's typed values.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
