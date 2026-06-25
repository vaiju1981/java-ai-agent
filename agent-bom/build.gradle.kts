plugins {
    `java-platform`
    `maven-publish`
    signing
}

description = "Bill of Materials (BOM) for java-ai-agent — import to align every module's version."

// Constrain every published library module to this build's version, so consumers import the BOM once
// and omit per-dependency versions. Applications (examples, demos, production-reference, apps/*) and
// this BOM itself are not listed. Publishing + signing are configured here because the root build only
// wires them for `java-library` modules, and a BOM is a `java-platform`.
dependencies {
    constraints {
        api(project(":agent-core"))
        api(project(":agent-langchain4j"))
        api(project(":agent-spring-ai"))
        api(project(":agent-anthropic"))
        api(project(":agent-openai"))
        api(project(":agent-spring-boot-starter"))
        api(project(":agent-adk"))
        api(project(":agent-mcp"))
        api(project(":agent-observability-otel"))
        api(project(":agent-store-jdbc"))
        api(project(":agent-store-pgvector"))
        api(project(":agent-store-sqlite"))
        api(project(":agent-tools-jsonschema"))
        api(project(":agent-tools-annotations"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["javaPlatform"])
            pom {
                name.set(project.name)
                description.set(provider { project.description ?: "java-ai-agent BOM" })
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

// Sign when a GPG key is present (the CI release job); local/snapshot builds skip signing.
signing {
    val signingKey = System.getenv("SIGNING_KEY")
    val signingPassword = System.getenv("SIGNING_PASSWORD")
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications["maven"])
    }
}
