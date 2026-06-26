// Maven Central Portal publishing (aggregates all module publications). Applied here so the publish
// task lives at the root; credentials come from CENTRAL_* env in the release job.
plugins {
    id("com.gradleup.nmcp.settings") version "1.5.0"
}

rootProject.name = "java-ai-agent"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "agent-core",
    "agent-bom",
    "agent-langchain4j",
    "agent-spring-ai",
    "agent-adk",
    "agent-mcp",
    "agent-a2a",
    "agent-observability-otel",
    "agent-store-jdbc",
    "agent-store-pgvector",
    "agent-store-sqlite",
    "agent-tools-jsonschema",
    "agent-tools-annotations",
    "agent-anthropic",
    "agent-openai",
    "agent-spring-boot-starter",
    "examples",
    "downstream-smoke",
    "production-reference",
)

nmcpSettings {
    centralPortal {
        username = System.getenv("CENTRAL_USERNAME") ?: ""
        password = System.getenv("CENTRAL_TOKEN") ?: ""
        // Upload a deployment but require a manual "publish" in the Central Portal UI (safer default).
        publishingType = "USER_MANAGED"
    }
}
