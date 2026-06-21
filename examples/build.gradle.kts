plugins {
    application
}

description = "Runnable examples for java-ai-agent."

dependencies {
    implementation(project(":agent-core"))
    implementation(project(":agent-langchain4j"))
    runtimeOnly(libs.slf4j.simple)
}

application {
    mainClass.set("dev.vaijanath.aiagent.examples.HelloAgent")
}
