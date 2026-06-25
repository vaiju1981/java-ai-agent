plugins {
    `java-library`
}

description = "Durable CheckpointStore backed by embedded SQLite — crash-resumable orchestration."

dependencies {
    api(project(":agent-core"))

    // The SQLite JDBC driver is an internal detail; consumers get it transitively at runtime.
    implementation(libs.sqlite.jdbc)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// This module has no released baseline on Maven Central yet, so the API-compatibility guardrail has
// nothing to diff against. Re-enable it after the module's first release lands (see the japicmp config
// in the root build).
tasks.matching { it.name == "japicmpCheck" }.configureEach { enabled = false }
