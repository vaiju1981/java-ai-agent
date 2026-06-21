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
    "agent-observability-otel",
    "examples",
)
