plugins {
    java
}

description = "Downstream-consumer compile check: proves adapter dependency scopes are published as api."

dependencies {
    implementation(project(":agent-langchain4j"))
    implementation(project(":agent-spring-ai"))
    implementation(project(":agent-adk"))
    implementation(project(":agent-mcp"))
    implementation(project(":agent-observability-otel"))
}
