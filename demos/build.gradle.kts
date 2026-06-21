plugins {
    application
}

description = "Real-world demos showing java-ai-agent's usefulness."

dependencies {
    implementation(project(":agent-core"))
    implementation(project(":agent-langchain4j"))
    implementation(libs.sqlite.jdbc)
    implementation(libs.jackson.databind)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    // Override with -PmainClass=...<Demo> to run a different demo.
    mainClass.set(providers.gradleProperty("mainClass")
        .orElse("dev.vaijanath.aiagent.demos.DataAnalystDemo"))
    // SQLite loads a native lib; opt in so JDK 22+ doesn't warn.
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
