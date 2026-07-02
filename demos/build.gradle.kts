plugins {
    application
}

description = "Full-fledged showcase demos for java-ai-agent."

dependencies {
    implementation(project(":agent-core"))
    implementation(project(":agent-langchain4j"))
    implementation(libs.jackson.databind)
    implementation(libs.sqlite.jdbc)
    implementation(libs.duckdb.jdbc)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set(providers.gradleProperty("mainClass")
        .orElse("dev.vaijanath.aiagent.demos.IncidentResponseWarRoom"))
}

tasks.test {
    useJUnitPlatform {
        // Manual/real-data tests are excluded from the default (CI) run.
        // Run them explicitly with: ./gradlew :demos:test -PrunManual
        if (!project.hasProperty("runManual")) {
            excludeTags("manual")
        }
    }
}
