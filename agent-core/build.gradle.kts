plugins {
    `java-library`
}

description = "Core SPIs and the agent runtime — zero framework dependencies (SLF4J only)."

dependencies {
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
    // A real SLF4J provider at test time so MDC works in the context-propagation tests
    // (slf4j-simple/-nop ship a no-op MDCAdapter). Quieted via src/test/resources/logback-test.xml.
    testRuntimeOnly(libs.logback.classic)
}

// Coverage floors are enforced centrally for every java-library module in the root build.
