plugins {
    `java-library`
}

description = "Core SPIs and the agent runtime — zero framework dependencies (SLF4J only)."

// 0.3.0: `Message` gained a `media` component for multimodal input (images/audio). That is a
// deliberate, documented binary-incompatible change to its canonical constructor — see
// docs/MIGRATION-0.3.md. All in-repo construction goes through the unchanged factory methods, so
// only direct `new Message(...)` callers are affected. Exclude it from the japicmp guard for this
// release; remove this once the baseline is bumped to 0.3.0 (-PjapicmpBaseline=0.3.0) after release.
extra["japicmpExcludes"] = "dev.vaijanath.aiagent.model.Message"

dependencies {
    api(libs.slf4j.api)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.launcher)
}

// Coverage floors are enforced centrally for every java-library module in the root build.
