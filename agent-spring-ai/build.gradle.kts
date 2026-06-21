plugins {
    `java-library`
}

description = "Spring AI L0 adapter: a ModelPort backed by a Spring AI ChatModel."

dependencies {
    api(project(":agent-core"))

    implementation(platform(libs.spring.ai.bom))
    implementation(libs.spring.ai.client.chat)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
