plugins {
    `java-library`
}

description = "JSON-schema validation of tool arguments — a ToolArgumentValidator for agent-core."

dependencies {
    api(project(":agent-core"))

    // Used internally to parse arguments and the schema; not exposed on the public API.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
