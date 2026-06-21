plugins {
    `java-library`
}

description = "Durable JDBC-backed ConversationStore (SQLite, PostgreSQL, MySQL, …), queryable for analytics."

dependencies {
    api(project(":agent-core"))

    // Used internally to serialize tool calls into a column; not exposed on the public API.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    testImplementation(libs.sqlite.jdbc)
}
