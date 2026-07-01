import org.gradle.api.tasks.Copy

plugins {
    application
}

description = "Full-fledged showcase demos for java-ai-agent."

dependencies {
    implementation(project(":agent-core"))
    implementation(libs.jackson.databind)
    implementation(libs.sqlite.jdbc)
    implementation(libs.tablesaw.core)
    runtimeOnly(libs.slf4j.simple)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

application {
    mainClass.set(providers.gradleProperty("mainClass")
        .orElse("dev.vaijanath.aiagent.demos.IncidentResponseWarRoom"))
}

tasks.named<Copy>("processResources") {
    val analyticsUiDist = layout.projectDirectory.dir("analytics-ui/dist")
    from(analyticsUiDist) {
        into("analytics-ui")
    }
}
