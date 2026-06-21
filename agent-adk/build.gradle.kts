plugins {
    `java-library`
}

description = "Google ADK adapter: wrap an ADK agent as an Agent (agent-as-component)."

dependencies {
    api(project(":agent-core"))

    implementation(libs.google.adk)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
