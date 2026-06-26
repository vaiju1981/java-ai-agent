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
    id("com.diffplug.spotless") version "8.7.0"
}

// japicmp CLI version — run via JavaExec for the per-module API-compatibility guardrail (see subprojects).
// Defined here (not the version catalog) because the type-safe `libs` accessor isn't available inside the
// root `subprojects {}` block.
val japicmpVersion = "0.26.1"

allprojects {
    group = "io.github.vaiju1981"
    // Releases set RELEASE_VERSION (derived from the git tag) in CI; everything else is a snapshot.
    version = System.getenv("RELEASE_VERSION")?.takeIf(String::isNotBlank) ?: "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

// Code quality runs via SonarCloud's automatic analysis (GitHub App), so no Gradle Sonar wiring here.

// Combine every library module's Javadoc into one site for GitHub Pages.
tasks.register<Copy>("aggregateJavadoc") {
    description = "Aggregates each module's Javadoc into build/docs/javadoc (for GitHub Pages)."
    group = "documentation"
    val modules = listOf(
        "agent-core", "agent-langchain4j", "agent-spring-ai", "agent-anthropic", "agent-adk",
        "agent-mcp", "agent-observability-otel", "agent-store-jdbc", "agent-store-pgvector",
        "agent-tools-jsonschema", "agent-tools-annotations", "agent-spring-boot-starter")
    modules.forEach { name ->
        dependsOn(":$name:javadoc")
        from(project(":$name").layout.buildDirectory.dir("docs/javadoc")) { into(name) }
    }
    into(layout.buildDirectory.dir("docs/javadoc"))
    doLast {
        layout.buildDirectory.file("docs/javadoc/index.html").get().asFile.writeText(
            "<!doctype html><html><head><meta charset=\"utf-8\"><title>java-ai-agent Javadoc</title>"
                + "</head><body><h1>java-ai-agent Javadoc</h1><ul>"
                + modules.joinToString("") { "<li><a href=\"$it/index.html\">$it</a></li>" }
                + "</ul></body></html>")
    }
}

subprojects {
    // Pin CVE-flagged transitive deps to patched versions (Dependabot). netty/bouncycastle stay within
    // their minor line; OpenTelemetry is forced across the whole group to keep its core artifacts aligned
    // (the API/SDK are backward-compatible across the 1.x line) — google-adk drags in 1.51.0, which has a
    // medium-severity advisory in opentelemetry-api (<= 1.61.0). Keep this in step with the version catalog.
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group == "io.netty") {
                useVersion("4.2.15.Final")
            }
            if (requested.group == "org.bouncycastle") {
                useVersion("1.84")
            }
            if (requested.group == "io.opentelemetry") {
                useVersion("1.63.0")
            }
        }
    }

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
            // Floor = the always-on guard tests; the Testcontainers test lifts the real DB path to ~82%
            // line wherever Docker is available (CI + local), but is skipped on Docker-less machines.
            "agent-store-pgvector" to "0.25",
        ).getOrDefault(project.name, "0.60")
        val branchFloor = mapOf(
            "agent-core" to "0.60", "agent-store-jdbc" to "0.70", "agent-tools-jsonschema" to "0.70",
            "agent-mcp" to "0.45", "agent-spring-ai" to "0.45", "agent-observability-otel" to "0.15",
            "agent-adk" to "0.50", "agent-langchain4j" to "0.05",
            "agent-store-pgvector" to "0.45",
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

        // API-compatibility guardrail: diff each library module's public API against its last published
        // release on Maven Central and fail on binary-incompatible changes to non-@Internal API. This is
        // the enforcement behind the "deprecate, don't remove" policy (docs/API-STABILITY.md). Override the
        // baseline with -PjapicmpBaseline=<version>; bump it after a release once it lands on Central.
        val japicmpBaselineVersion =
            (project.findProperty("japicmpBaseline") as String?)?.takeIf(String::isNotBlank) ?: "0.4.0"
        val japicmpTool = configurations.create("japicmpTool") { isCanBeConsumed = false }
        val japicmpBaseline = configurations.create("japicmpBaseline") {
            isCanBeConsumed = false
            // Only the baseline jar is diffed; its transitive deps are resolved leniently at compare time.
            isTransitive = false
        }
        dependencies {
            add("japicmpTool", "com.github.siom79.japicmp:japicmp:$japicmpVersion:jar-with-dependencies")
            add("japicmpBaseline", "${project.group}:${project.name}:$japicmpBaselineVersion")
        }
        val japicmpReport = layout.buildDirectory.file("reports/japicmp/${project.name}.html")
        val japicmpCheck = tasks.register<JavaExec>("japicmpCheck") {
            description = "Checks public API binary compatibility against $japicmpBaselineVersion (Maven Central)."
            group = "verification"
            val jarTask = tasks.named("jar")
            dependsOn(jarTask)
            // Track the jar as an input (not just a dependency) so a changed API re-runs the check rather
            // than being skipped as up-to-date when only the jar changed.
            inputs.files(jarTask).withPropertyName("subjectJar")
            inputs.files(japicmpBaseline).withPropertyName("baseline")
            outputs.file(japicmpReport)
            classpath = japicmpTool
            mainClass.set("japicmp.JApiCmp")
            doFirst {
                japicmpReport.get().asFile.parentFile.mkdirs()
                // Always exclude @Internal-annotated elements. A module may also exclude classes by name
                // via the `japicmpExcludes` extra property (semicolon-separated) — needed for elements that
                // gained @Internal only in the new version (annotation-exclude can't match the old jar), e.g.
                // Spring autoconfiguration classes whose @Bean method signatures are not a user-facing API.
                val byName = (project.findProperty("japicmpExcludes") as String?)?.takeIf(String::isNotBlank)
                val excludes = "@dev.vaijanath.aiagent.annotation.Internal" + (byName?.let { ";$it" } ?: "")
                args = listOf(
                    "--old", japicmpBaseline.singleFile.absolutePath,
                    "--new", jarTask.get().outputs.files.singleFile.absolutePath,
                    "-a", "public",
                    "--only-modified",
                    "--ignore-missing-classes",
                    "--error-on-binary-incompatibility",
                    "--exclude", excludes,
                    "--html-file", japicmpReport.get().asFile.absolutePath,
                )
            }
        }
        tasks.named("check") {
            dependsOn(japicmpCheck)
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

        // Sign artifacts when a GPG key is provided (the CI release job); snapshot and local builds
        // have no key and skip signing, so they still build without one.
        apply(plugin = "signing")
        extensions.configure<org.gradle.plugins.signing.SigningExtension> {
            val signingKey = System.getenv("SIGNING_KEY")
            val signingPassword = System.getenv("SIGNING_PASSWORD")
            if (!signingKey.isNullOrBlank()) {
                useInMemoryPgpKeys(signingKey, signingPassword)
                sign(extensions.getByType<PublishingExtension>().publications["maven"])
            }
        }
    }
}
