import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions

// Root build — shared configuration for every module.

allprojects {
    group = "io.github.vaiju1981"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    // Target Java 21 (the baseline consumers need) even though we build on a newer JDK.
    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }

    // Publishing for library modules only (the examples application is not published).
    plugins.withId("java-library") {
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
        }
    }
}
