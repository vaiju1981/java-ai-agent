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
