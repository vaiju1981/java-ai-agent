plugins {
    `java-library`
}

description = "First reference L0 adapter: a ModelPort backed by LangChain4j (incl. local Ollama)."

dependencies {
    api(project(":agent-core"))

    implementation(platform(libs.langchain4j.bom))
    implementation(libs.langchain4j.core)
    implementation(libs.langchain4j.ollama)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}
