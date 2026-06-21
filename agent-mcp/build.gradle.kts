plugins {
    `java-library`
}

description = "MCP adapter: expose Model Context Protocol server tools as java-ai-agent Tools."

dependencies {
    api(project(":agent-core"))

    implementation(platform(libs.langchain4j.bom))
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.mcp)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
