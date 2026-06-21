plugins {
    application
}

description = "Runnable examples for java-ai-agent."

dependencies {
    implementation(project(":agent-core"))
    implementation(project(":agent-langchain4j"))
    implementation(libs.jackson.databind)
    runtimeOnly(libs.slf4j.simple)
}

application {
    // Override with -PmainClass=...SafeAgent to run a different example.
    mainClass.set(providers.gradleProperty("mainClass")
        .orElse("dev.vaijanath.aiagent.examples.HelloAgent"))
}
