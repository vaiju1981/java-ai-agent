plugins {
    `java-library`
}

description = "MCP adapter: expose Model Context Protocol server tools as java-ai-agent Tools."

dependencies {
    api(project(":agent-core"))

    // McpTools.from(McpClient) exposes the langchain4j-mcp type, so api; core stays internal.
    api(platform(libs.langchain4j.bom))
    implementation(libs.langchain4j.core)
    api(libs.langchain4j.mcp)

    // Renders the MCP server's parameter schema into a JSON-schema string; not on the public API.
    implementation(libs.jackson.databind)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    // Verifies the propagated schema is actually enforceable by the JSON-schema validator.
    testImplementation(project(":agent-tools-jsonschema"))
}
