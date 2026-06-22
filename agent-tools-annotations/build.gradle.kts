plugins {
    `java-library`
}

description = "Annotation-driven tools — derive a ToolSpec + JSON schema from annotated Java methods."

dependencies {
    api(project(":agent-core"))

    // Reflective JSON-schema generation and argument binding; not exposed on the public API.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    // Proves generated schemas are enforceable by the validator (composition test).
    testImplementation(project(":agent-tools-jsonschema"))
}
