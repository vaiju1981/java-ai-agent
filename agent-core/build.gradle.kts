import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

plugins {
    `java-library`
}

description = "Core SPIs and the agent runtime — zero framework dependencies (SLF4J only)."

dependencies {
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// Enforce a coverage floor on the core (currently ~81% line coverage); part of `check`.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    violationRules {
        rule {
            limit {
                counter = "LINE"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
tasks.named("check") {
    dependsOn("jacocoTestCoverageVerification")
}
