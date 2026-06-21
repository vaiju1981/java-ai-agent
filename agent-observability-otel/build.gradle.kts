plugins {
    `java-library`
}

description = "OpenTelemetry tracing adapter — maps AgentObserver events to spans."

dependencies {
    api(project(":agent-core"))

    // OtelAgentObserver's public constructor takes an OpenTelemetry Tracer, so api.
    api(platform(libs.opentelemetry.bom))
    api(libs.opentelemetry.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)

    testImplementation(platform(libs.opentelemetry.bom))
    testImplementation(libs.opentelemetry.sdk)
    testImplementation(libs.opentelemetry.sdk.testing)
}
