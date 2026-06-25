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
