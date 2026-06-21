plugins {
    `java-library`
}

description = "First reference L0 adapter: a ModelPort backed by LangChain4j (incl. local Ollama)."

dependencies {
    api(project(":agent-core"))

    implementation(platform(libs.langchain4j.bom))
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.ollama)
    // Parses MCP-style JSON parameter schemas into LangChain4j's schema types.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
