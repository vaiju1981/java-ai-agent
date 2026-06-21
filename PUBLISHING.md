# Publishing

The library modules (`agent-core`, `agent-langchain4j`, `agent-spring-ai`, `agent-adk`,
`agent-observability-otel`) are configured for Maven publication with sources + javadoc jars and
full POM metadata. The `examples` module is not published.

## Try it locally (works today)

```bash
./gradlew publishToMavenLocal
# -> ~/.m2/repository/io/github/vaiju1981/<module>/<version>/  (jar, -sources, -javadoc, .pom)
```

Then depend on it from another project:

```kotlin
dependencies {
    implementation("io.github.vaiju1981:agent-core:0.1.0-SNAPSHOT")
    implementation("io.github.vaiju1981:agent-langchain4j:0.1.0-SNAPSHOT")
}
```

## Publishing to Maven Central (remaining setup)

Group `io.github.vaiju1981` is verifiable via the GitHub namespace on the
[Central Portal](https://central.sonatype.com). To publish a release:

1. **Verify the namespace** `io.github.vaiju1981` on the Central Portal (one-time).
2. **Add signing** — Central requires signed artifacts. Add the `signing` plugin and a GPG key
   (`signing.gnupg.keyName`), and sign the `maven` publication.
3. **Add credentials** — a Central Portal user token, supplied via `~/.gradle/gradle.properties`
   (never commit it).
4. **Drop `-SNAPSHOT`** from the version for a release.
5. **Publish** via the Central Portal (e.g. the `com.vanniktech.maven.publish` plugin, or the
   Sonatype Central upload). 

These steps need the author's Central account + signing key, so they're documented rather than
wired in. The publication itself (coordinates, jars, POM) is already correct — confirmed by
`publishToMavenLocal`.
