plugins {
    `java-library`
}

description = "OpenTelemetry tracing adapter — maps AgentObserver events to spans."

dependencies {
    api(project(":agent-core"))

    implementation(platform(libs.opentelemetry.bom))
    implementation(libs.opentelemetry.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)

    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.sdk.testing)
}
