plugins {
    `java-library`
}

description = "Direct OpenAI ModelPort — a ModelPort backed by the official OpenAI Java SDK."

dependencies {
    api(project(":agent-core"))

    // The official OpenAI Java SDK — talks to the Chat Completions API directly, no intermediary.
    implementation(libs.openai.java)
    // Used to bridge our JSON-schema tool specs and tool arguments to the SDK's typed values.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
