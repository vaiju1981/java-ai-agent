rootProject.name = "java-ai-agent"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "agent-core",
    "agent-langchain4j",
    "agent-spring-ai",
    "agent-adk",
    "agent-mcp",
    "agent-observability-otel",
    "examples",
    "demos",
)
