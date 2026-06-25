# Publishing

All `java-library` modules (everything under `agent-*`) are published to Maven Central under
`io.github.vaiju1981`, with sources + javadoc jars and full POM metadata. The `examples`, `demos`,
`production-reference`, and `apps/*` modules are applications and are **not** published.

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

## Publishing to Maven Central (live)

Releases are on Maven Central under `io.github.vaiju1981` — currently **0.2.0** (plus 0.1.0–0.1.2).
The namespace is verified, artifacts are signed, and a release is driven by the `release` GitHub
Actions workflow on a version tag.

Per-release process:

1. **Tag the release** — `RELEASE_VERSION` drives the build version (the default is `0.1.0-SNAPSHOT`
   for local/dev builds).
2. **CI builds, signs, and uploads** a deployment to the Central Portal (credentials from `CENTRAL_*`
   secrets; GPG signing key in CI).
3. **Publish the deployment** in the Central Portal UI — it is uploaded as `USER_MANAGED` (see the
   `nmcpSettings` in `settings.gradle.kts`), so a human clicks **Publish** as the safe final gate.
4. **After it lands**, bump the japicmp baseline (`-PjapicmpBaseline`) to the released version and
   update the [CHANGELOG](CHANGELOG.md).

Signing keys and Portal credentials live in CI secrets / `~/.gradle/gradle.properties`, never in the repo.
