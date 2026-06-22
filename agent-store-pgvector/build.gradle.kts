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
    // The driver for the POSTGRES_TEST_URL-gated integration test (runs in CI against pgvector).
    testImplementation(libs.postgresql)
}
