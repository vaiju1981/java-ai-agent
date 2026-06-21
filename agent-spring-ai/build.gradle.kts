plugins {
    `java-library`
}

description = "Spring AI L0 adapter: a ModelPort backed by a Spring AI ChatModel."

dependencies {
    api(project(":agent-core"))

    // SpringAiModelPort's public constructor takes a Spring AI ChatModel, so api.
    api(platform(libs.spring.ai.bom))
    api(libs.spring.ai.client.chat)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
