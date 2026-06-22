import com.diffplug.gradle.spotless.SpotlessExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

// Root build — shared configuration for every module.

plugins {
    id("com.diffplug.spotless") version "7.0.2"
}

allprojects {
    group = "io.github.vaiju1981"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Formatting + import hygiene for every module; `spotlessCheck` runs as part of `check`.
    apply(plugin = "com.diffplug.spotless")
    configure<SpotlessExtension> {
        java {
            target("src/**/*.java")
            // Use the JavaParser-based remover (not google-java-format), which doesn't touch
            // jdk.compiler internals — so it works on any JDK, including newer ones than the build's.
            removeUnusedImports("cleanthat-javaparser-unnecessaryimport")
            importOrder()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }

    // Library modules: coverage report + enforced floor + publishing.
    plugins.withId("java-library") {
        apply(plugin = "jacoco")
        tasks.named("test") {
            finalizedBy(tasks.named("jacocoTestReport"))
        }
        tasks.withType<JacocoReport>().configureEach {
            reports {
                xml.required.set(true)
            }
        }

        // Repo-wide coverage floors, enforced as part of `check`, so coverage can't silently regress.
        // Set just below each module's current level. Thin adapters over live systems (langchain4j)
        // are floored low because their logic genuinely needs a running backend to exercise.
        val lineFloor = mapOf(
            "agent-core" to "0.80", "agent-store-jdbc" to "0.85", "agent-tools-jsonschema" to "0.80",
            "agent-mcp" to "0.70", "agent-spring-ai" to "0.65", "agent-observability-otel" to "0.70",
            "agent-adk" to "0.45", "agent-langchain4j" to "0.10",
        ).getOrDefault(project.name, "0.60")
        val branchFloor = mapOf(
            "agent-core" to "0.60", "agent-store-jdbc" to "0.70", "agent-tools-jsonschema" to "0.70",
            "agent-mcp" to "0.45", "agent-spring-ai" to "0.45", "agent-observability-otel" to "0.15",
            "agent-adk" to "0.50", "agent-langchain4j" to "0.05",
        ).getOrDefault(project.name, "0.40")
        tasks.withType<JacocoCoverageVerification>().configureEach {
            violationRules {
                rule {
                    limit {
                        counter = "LINE"
                        value = "COVEREDRATIO"
                        minimum = lineFloor.toBigDecimal()
                    }
                    limit {
                        counter = "BRANCH"
                        value = "COVEREDRATIO"
                        minimum = branchFloor.toBigDecimal()
                    }
                }
            }
        }
        tasks.named("check") {
            dependsOn(tasks.withType<JacocoCoverageVerification>())
        }

        apply(plugin = "maven-publish")
        extensions.configure<JavaPluginExtension> {
            withSourcesJar()
            withJavadocJar()
        }
        tasks.withType<Javadoc>().configureEach {
            isFailOnError = false
            (options as StandardJavadocDocletOptions).addBooleanOption("Xdoclint:none", true)
        }
        extensions.configure<PublishingExtension> {
            publications {
                create<MavenPublication>("maven") {
                    from(components["java"])
                    pom {
                        name.set(project.name)
                        description.set(provider { project.description ?: "java-ai-agent module" })
                        url.set("https://github.com/vaiju1981/java-ai-agent")
                        licenses {
                            license {
                                name.set("The Apache License, Version 2.0")
                                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            }
                        }
                        developers {
                            developer {
                                id.set("vaiju1981")
                                name.set("Vaijanath Rao")
                            }
                        }
                        scm {
                            url.set("https://github.com/vaiju1981/java-ai-agent")
                            connection.set("scm:git:https://github.com/vaiju1981/java-ai-agent.git")
                        }
                    }
                }
            }
            repositories {
                // Credentials come from ~/.gradle/gradle.properties (never the repo).
                maven {
                    name = "centralSnapshots"
                    setUrl("https://central.sonatype.com/repository/maven-snapshots/")
                    credentials {
                        username = project.findProperty("centralUsername") as String?
                        password = project.findProperty("centralPassword") as String?
                    }
                }
            }
        }
    }
}
