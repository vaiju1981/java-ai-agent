plugins {
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // One BOM pins every java-ai-agent module to the same version.
    implementation(platform("io.github.vaiju1981:agent-bom:0.4.0"))
    implementation("io.github.vaiju1981:agent-core")
    implementation("io.github.vaiju1981:agent-anthropic") // talk to Claude; swap for agent-openai for GPT
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("com.example.Main")
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
}
