plugins {
    `java-library`
}

description = "pgvector-backed Retriever — a native vector column + ANN cosine search on PostgreSQL."

dependencies {
    api(project(":agent-core"))
    // Reuses ConnectionSource; both are JDBC-backed stores.
    api(project(":agent-store-jdbc"))

    // Serializes chunk metadata to a JSON column; not exposed on the public API.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    // The Postgres JDBC driver, used by the Testcontainers integration test.
    testImplementation(libs.postgresql)
    // Testcontainers spins a real Postgres+pgvector container so the ANN path is covered self-containedly
    // (no external service needed); the test is skipped automatically where Docker is unavailable.
    testImplementation(platform(libs.testcontainers.bom))
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit)
}
