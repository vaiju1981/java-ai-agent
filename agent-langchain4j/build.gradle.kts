plugins {
    `java-library`
}

description = "First reference L0 adapter: a ModelPort backed by LangChain4j (incl. local Ollama)."

dependencies {
    api(project(":agent-core"))

    // Exposed on public entry points (ChatModel, StreamingChatModel, EmbeddingModel), so api.
    api(platform(libs.langchain4j.bom))
    api(libs.langchain4j.core)
    // Used only internally to build Ollama-backed ports; not exposed.
    implementation(libs.langchain4j.ollama)
    // Parses MCP-style JSON parameter schemas into LangChain4j's schema types.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
